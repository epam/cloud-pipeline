import * as cp from "child_process";
import * as vscode from "vscode";
import * as readline from "node:readline";
import { EventEmitter } from "events";

import { Disposable } from "./common/disposable";
import { findRandomPort } from "./common/ports";
import { ILogger, Logger, LogLevel } from "./common/logger";
import { ICpExtConfig } from "./config";

// line: "2025-09-16 13:48:07,888:INFO: Searching for tunnel processes..."
const pipeLineRe: RegExp =
  /^(?:\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}):([^:]+):\s*(.*)$/;

enum PipeTunnelState {
  none = 0,
  starting = 1,
  listed = 2,
  started = 3,
  stopping = 4,
  stopped = 5,
  error = 100,
}

export class PipeTunnel extends Disposable {
  private readonly output: Logger;

  private state: PipeTunnelState = PipeTunnelState.none;

  private child: {
    process: cp.ChildProcessWithoutNullStreams;
    localPort: number;
    pipeTunnelStdout: readline.Interface;
    pipeTunnelStderr: readline.Interface;
  } | null = null;

  private readonly eventEmitter: EventEmitter = new EventEmitter();

  constructor(
    private readonly cpRunId: number,
    private readonly cpExtConfig: ICpExtConfig,
    private readonly logger: ILogger,
  ) {
    super();

    this.output = new Logger(`${this.cpExtConfig.prefix} tunnel ${cpRunId}`);
    this._register(this.output);
  }

  override dispose() {
    if (!this.isDisposed) {
      if (this.child) {
        console.log(`PipeTunnel: terminating process ${this.cpRunId} ...`);
        void this.deactivate();
        console.log(`PipeTunnel: terminated process ${this.cpRunId}`);
      }
      this.output.dispose();
      this.eventEmitter.removeAllListeners();
    }
    super.dispose();
  }

  private stop(): void {
    // prettier-ignore
    const _process = cp.spawn("pipe", [
        "tunnel",
        "stop",
        this.cpRunId.toString(),
        "-lp",
        this.child!.localPort.toString(),
      ], {
        shell: true,
      },
    );
  }

  private start(
    progress: vscode.Progress<{
      message?: string;
      increment?: number;
    }>,
  ) {
    if (!this.child) {
      throw new Error("PipeTunnel: start() called with no child process");
    }
    this.state = PipeTunnelState.starting;

    this.child.pipeTunnelStdout.on("line", (line) => {
      this.output.appendLine(line);
    });

    this.child.pipeTunnelStderr.on("line", (line) => {
      const match = line.match(pipeLineRe);
      const level: LogLevel | undefined = match
        ? (match[1].toLowerCase() as LogLevel)
        : undefined;
      const message = match ? match[2] : line;

      if (
        level &&
        (this.state != PipeTunnelState.started ||
          ["error", "warn"].includes(level))
      ) {
        this.output.log(level, message);
      }

      if (!level && line.toLowerCase().startsWith("error:")) {
        this.eventEmitter.emit("processError", new Error(line.slice(7)));
      }
      let currentProgress = 10;

      function progressReport(reachedProgress: number, msg: string) {
        progress.report({
          increment: reachedProgress - currentProgress,
          message: msg,
        });
        currentProgress = reachedProgress;
      }

      if (message.startsWith("Searching for processes listening local ports")) {
        progressReport(15, message);
      } else if (message.startsWith("Configuring putty and openssh password")) {
        progressReport(25, `${message}`);
      } else if (message.startsWith("Initializing passwordless")) {
        progressReport(28, `${message}`);
      } else if (message.startsWith("Copying remote ppk key...")) {
        progressReport(46, `${message}`);
      } else if (message.startsWith("Appending host record to putty")) {
        progressReport(74, `${message}`);
      } else if (message.startsWith("Calculating putty host hash")) {
        progressReport(87, `${message}`);
      } else if (message.startsWith("Waiting for connections")) {
        progressReport(100, `${message}`);
        this.state = PipeTunnelState.listed;
        this.eventEmitter.emit("listed");

        this.state = PipeTunnelState.started;
        this.eventEmitter.emit("ready");
      } else {
        progress.report({ message: `${message}` });
      }
    });

    this.child.process.on("error", (err) => {
      console.log("PipeTunnel: process.on error");
      this.eventEmitter.emit("processError", err);
    });

    this.child.process.on("close", (code) => {
      console.log("PipeTunnel: process.on close");
      this.eventEmitter.emit("processClose", code);
      this.child = null;
    });
  }

  on(event: string, listener: (...args: any[]) => void): void {
    this.eventEmitter.on(event, listener);
  }

  public async activate(
    startProcess: (
      localPort: number,
      progress: vscode.Progress<{ message?: string; increment?: number }>,
    ) => Promise<cp.ChildProcessWithoutNullStreams>,
  ): Promise<void> {
    return vscode.window.withProgress<void>(
      {
        location: vscode.ProgressLocation.Notification,
        title: `Starting ${this.cpExtConfig.prefix} tunnel ${this.cpRunId}: \n`,
        cancellable: false,
      },
      async (progress, _token) => {
        progress.report({ increment: 0 });

        const localPort = await findRandomPort();
        progress.report({
          increment: 10,
          message: `\nFound random local port ${localPort}...`,
        });

        return new Promise<void>((resolve, reject) => {
          this.on("ready", () => {
            resolve();
          });
          this.on("processError", (err) => {
            reject(err);
          });
          this.on("processClose", (code) => {
            if (code === 0) {
              reject(new Error("Tunnel process closed (exitcode: 0)"));
            } else {
              reject(new Error(`Tunnel process error (exitcode: ${code})`));
            }
          });

          // Start process after all subscriptions are set
          startProcess(localPort, progress)
            .then((process) => {
              this.logger.info(
                `PipeTunnel: started process (pid: ${process.pid})`,
              );

              this.child = {
                process,
                localPort,
                pipeTunnelStdout: readline.createInterface({
                  input: process.stdout,
                }),
                pipeTunnelStderr: readline.createInterface({
                  input: process.stderr,
                }),
              };

              this.start(progress);
            })
            .catch((err) => {
              reject(err);
            });
        });
      },
    );
  }

  public async deactivate(): Promise<void> {
    await new Promise<void>((resolve, _reject) => {
      if (this.state >= PipeTunnelState.listed) {
        resolve();
      } else {
        this.on("listed", () => {
          resolve();
        });
      }
    });
    return new Promise<void>((resolve, _reject) => {
      this.on("processClose", (_code) => {
        resolve();
      });
      this.stop();
    });
  }
}
