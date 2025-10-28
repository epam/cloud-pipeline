import * as cp from "child_process";
import * as vscode from "vscode";
import * as readline from "node:readline";
import { EventEmitter } from "events";

import { ILogger, OutputLogger, LogLevelName } from "./common/logger";
import { ICpExtConfig } from "./config";
import { PipeTunnelBase } from "./cp-client/tunnel/pipe-tunnel-base";
import { CpVersionInfo, PipeTunnelInfo } from "./cp-client";

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

export class PipeTunnel extends PipeTunnelBase {
  private readonly output: OutputLogger;

  private state: PipeTunnelState = PipeTunnelState.none;

  private child: {
    process: cp.ChildProcessWithoutNullStreams;
    localPort: number;
    owner: string;
    pipeTunnelStdout: readline.Interface;
    pipeTunnelStderr: readline.Interface;
  } | null = null;

  private readonly eventEmitter: EventEmitter = new EventEmitter();

  private _toStop: boolean;
  override get toStop(): boolean {
    return this._toStop;
  }

  constructor(
    runId: number,
    localPort: number,
    toStop: boolean,
    private readonly cpExtConfig: ICpExtConfig,
    private readonly logger: ILogger,
  ) {
    super(runId, localPort);
    this._toStop = toStop;
    const outputName = `${this.cpExtConfig.prefix} tunnel ${runId}`;
    this.output = new OutputLogger(outputName, "trace");
    this._register(this.output);
  }

  override dispose() {
    this.logger.trace(
      `${this.toLog()}.dispose(), start,\n` +
        `  isDisposed: ${this.isDisposed}, child: ${this.child}, toStop: ${this.toStop}`,
    );
    if (!this.isDisposed) {
      void (async () => {
        if (this.child) {
          console.log(`PipeTunnel: terminating process ${this.runId} ...`);
          await this.deactivate();
          console.log(`PipeTunnel: terminated process ${this.runId}`);
        }
        this.output.dispose();
        this.eventEmitter.removeAllListeners();
      })();
    }
    this.logger.trace(`${this.toLog()}.dispose(), super`);
    super.dispose();
    this.logger.trace(`${this.toLog()}.dispose(), end`);
  }

  override getInfo(): PipeTunnelInfo {
    return new PipeTunnelInfo(
      this.child!.process.pid!,
      null,
      this.child!.owner,
      this.runId,
      this.localPort,
      22,
    );
  }

  private stop(): void {
    // prettier-ignore
    const _process = cp.spawn("pipe", [
        "tunnel",
        "stop",
        this.runId.toString(),
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

    let currentProgress = 10;
    this.child.pipeTunnelStderr.on("line", (line) => {
      const match = line.match(pipeLineRe);
      const level: LogLevelName | undefined = match
        ? (match[1].toLowerCase() as LogLevelName)
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

      const progressReport = (reachedProgress: number, msg: string): void => {
        progress.report({
          increment: reachedProgress - currentProgress,
          message: msg,
        });
        currentProgress = reachedProgress;
      };

      if (message.startsWith("Searching for processes listening local ports")) {
        progressReport(15, message);
      } else if (message.startsWith("Configuring putty and openssh")) {
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
      toStop: boolean,
      progress: vscode.Progress<{ message?: string; increment?: number }>,
    ) => Promise<[cp.ChildProcessWithoutNullStreams, CpVersionInfo]>,
  ): Promise<void> {
    const logPfx = `${this.toLog()}.activate()`;
    this.logger.trace(`${logPfx}, in`);
    return vscode.window.withProgress<void>(
      {
        location: vscode.ProgressLocation.Notification,
        title: `Starting ${this.cpExtConfig.prefix} tunnel ${this.runId}: \n`,
        cancellable: false,
      },
      async (progress, cancelToken) => {
        this.logger.trace(`${logPfx}, start withProgress`);
        progress.report({ increment: 0 });

        return new Promise<void>((resolve, reject) => {
          const logPfx2 = `${logPfx}.promise`;
          this._register(
            cancelToken.onCancellationRequested((_event) => {
              // FIX: Stop process
              reject(new Error("Cancelled"));
            }),
          );

          this.on("ready", () => {
            this.logger.trace(`${logPfx2}, on ready -> resolve`);
            resolve();
          });
          this.on("processError", (err) => {
            this.logger.trace(`${logPfx2}, on processError -> reject`);
            reject(err);
          });
          this.on("processClose", (code) => {
            this.logger.trace(
              `${logPfx2}, on processClose( code: ${code} ) -> reject`,
            );
            if (code === 0) {
              reject(new Error("Tunnel process closed (exitcode: 0)"));
            } else {
              reject(new Error(`Tunnel process error (exitcode: ${code})`));
            }
          });

          this.logger.trace(
            `${logPfx}, process starting, ` +
              `localPort: ${this.localPort}, toStop: ${this.toStop} ...`,
          );
          // Start process after all subscriptions are set
          startProcess(this.localPort, this.toStop, progress)
            .then(([process, version]) => {
              this.logger.info(
                `${logPfx}, process started (pid: ${process.pid})`,
              );

              this.child = {
                process,
                localPort: this.localPort,
                owner: version.tokenOwner,
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
        this.logger.trace(`${logPfx}, end withProgress`);
      },
    );
    this.logger.trace(`${logPfx}, out`);
  }

  public async deactivate(): Promise<void> {
    const logPfx = `${this.toLog()}.deactivate()`;
    this.logger.trace(`${logPfx}, start, toStop: ${this.toStop}`);
    if (!this.toStop) {
      this.logger.debug(`${logPfx}, toStop is false, process unref.`);
      this.child!.process.unref();
    } else {
      this.logger.debug(`${logPfx}, toStop is true, process stopping...`);
      await new Promise<void>((resolve, _reject) => {
        this.on("listed", () => {
          this.logger.debug(`${logPfx}, process ready to stop`);
          resolve();
        });
        if (this.state >= PipeTunnelState.listed) {
          resolve();
        }
      });
      await new Promise<void>((resolve, _reject) => {
        this.on("processClose", (_code) => {
          this.logger.debug(`${logPfx}, process stopped.`);
          resolve();
        });
        this.logger.debug(`${logPfx}, process stopping...`);
        this.stop();
      });
    }
    this.logger.trace(`${logPfx}, end`);
  }
}
