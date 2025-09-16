import * as cp from "child_process";
import * as vscode from "vscode";
import * as readline from "node:readline";
import { EventEmitter } from "events";
import treeKill from "tree-kill";

import { Disposable } from "./common/disposable";
import { findRandomPort } from "./common/ports";
import { Logger, LogLevel } from "./common/logger";

// line: "2025-09-16 13:48:07,888:INFO: Searching for tunnel processes..."
const pipeLineRe: RegExp =
  /^(?:\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}):([^:]+):\s*(.*)$/;

export class PipeTunnel extends Disposable {
  private readonly output: Logger;
  private readonly pipeTunnelStdout: readline.Interface;
  private readonly pipeTunnelStderr: readline.Interface;

  private ready: boolean = false;
  private readonly eventEmitter: EventEmitter = new EventEmitter();

  constructor(
    private readonly cpRunId: number,
    private readonly process: cp.ChildProcessWithoutNullStreams,
    private readonly progress: vscode.Progress<{
      message?: string;
      increment?: number;
    }>,
  ) {
    super();

    this.output = new Logger(`Cloud Pipeline tunnel ${cpRunId}`);
    this.pipeTunnelStdout = readline.createInterface({
      input: this.process.stdout,
    });
    this.pipeTunnelStderr = readline.createInterface({
      input: this.process.stderr,
    });

    this.pipeTunnelStdout.on("line", (line) => {
      this.output.appendLine(line);
    });

    this.pipeTunnelStderr.on("line", (line) => {
      const match = line.match(pipeLineRe);
      const level: LogLevel = match
        ? (match[1].toLowerCase() as LogLevel)
        : "info";
      const message = match ? match[2] : line;

      if (!this.ready || ["error", "warn"].includes(level)) {
        this.output.log(level, message);
      }

      if (message.startsWith("Searching for processes listening local ports")) {
        this.progress.report({ increment: 5, message: `${message}` });
      } else if (message.startsWith("Configuring putty and openssh password")) {
        this.progress.report({ increment: 10, message: `${message}` });
      } else if (message.startsWith("Initializing passwordless")) {
        this.progress.report({ increment: 3, message: `${message}` });
      } else if (message.startsWith("Copying remote ppk key...")) {
        this.progress.report({ increment: 18, message: `${message}` });
      } else if (message.startsWith("Appending host record to putty")) {
        this.progress.report({ increment: 28, message: `${message}` });
      } else if (message.startsWith("Calculating putty host hash")) {
        this.progress.report({ increment: 13, message: `${message}` });
      } else if (message.startsWith("Waiting for connections")) {
        this.progress.report({ increment: 13, message: `${message}` });
        this.ready = true;
        this.eventEmitter.emit("ready");
      } else {
        this.progress.report({ message: `${message}` });
      }
    });
  }

  on(event: string, listener: (...args: any[]) => void): void {
    this.eventEmitter.on(event, listener);
  }

  override dispose() {
    this.eventEmitter.removeAllListeners();
    if (!this.isDisposed) {
      treeKill(this.process.pid!);
    }
    super.dispose();
  }
}

export async function execPipeTunnel(
  context: vscode.ExtensionContext,
  cpRunId: number,
): Promise<PipeTunnel> {
  return vscode.window.withProgress<PipeTunnel>(
    {
      location: vscode.ProgressLocation.Notification,
      title: `Starting CP tunnel ${cpRunId}: \n`,
      cancellable: false,
    },
    async (progress, _token) => {
      progress.report({ increment: 0 });

      const localPort = await findRandomPort();
      progress.report({
        increment: 10,
        message: `\nFound random local port ${localPort}...`,
      });

      // prettier-ignore
      const pipeTunnelChild = cp.spawn("pipe", [
        "tunnel", "start", "-f", "--ssh",
        "-rp", "22",
        "-lp", localPort.toString(),
        "--log-level", "INFO",
        cpRunId.toString(),
      ], { shell: true});

      return new Promise<PipeTunnel>((resolve, reject) => {
        const pipeTunnel = new PipeTunnel(cpRunId, pipeTunnelChild, progress);
        pipeTunnel.on("ready", () => {
          resolve(pipeTunnel);
        });

        pipeTunnel.on("error", (err) => {
          reject(err);
        });

        pipeTunnelChild.on("close", (code) => {
          if (code === 0) {
            reject(new Error("Tunnel process closed (exitcode: 0)"));
          } else {
            reject(new Error(`Tunnel process error (exitcode: ${code})`));
          }
        });
      });
    },
  );
}
