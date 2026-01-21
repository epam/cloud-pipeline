import * as vscode from "vscode";
import * as fs from "fs";
import * as cp from "child_process";
import tmp from "tmp";
import { rimraf } from "rimraf";
import * as path from "path";

import { getPipePlatform, PipePlatform, pipeUris } from "./pipe-uris";
import { downloadFile } from "../common/files/downloadFile";
import { unzipperFile } from "../common/files/unzipperFile";
import { untarFile } from "../common/files/untarFile";
import { ICpExtConfig } from "../config";
import { CpClientBase, CpVersionInfo, ExecPipeTunnelInfo } from "./index";
import { ILogger } from "../common/logger";
import {
  CpTokenExpiredError,
  DependentAbortError,
  UserCancelledError,
} from "./error";
import { askUserForUpdatePipe } from "./ask-user-for-update-pipe";
import { ICpCodeContext } from "../cp-ext/code-context";
import {
  quickPickWithCountdown,
  ActionQuickPickItem,
} from "../common/quick-pick-with-countdown";
import { waitForProcessExit } from "../common/process";

export class CpClient extends CpClientBase {
  static createAndRegister(
    cpExtConfig: ICpExtConfig,
    context: vscode.ExtensionContext,
    codeContext: ICpCodeContext,
    logger: ILogger,
  ): CpClient {
    const res = new CpClient(cpExtConfig, codeContext, logger);
    context.subscriptions.push(res);
    return res;
  }

  public override async ensurePipeExecDo(
    forceUpdate: boolean = false,
  ): Promise<CpVersionInfo> {
    const fsp = fs.promises;

    // choose URL based on platform
    const platform: PipePlatform = getPipePlatform();
    const pipeUri: string = pipeUris[platform].uri;

    const binDir = getBinDir(this.cpExtConfig);
    const binPipeDir = path.join(binDir, "pipe");
    this.pipeExec = path.join(binPipeDir, pipeUris[platform].exec);
    this.logger.info(`${this.toLog()} pipeExec:\n` + `  ${this.pipeExec}`);
    // check if binDir exists and is non-empty
    let needsDownload = true;
    try {
      const stat = await fsp.stat(binPipeDir);
      if (stat.isDirectory()) {
        const files = await fsp.readdir(binPipeDir);
        if (files.length > 0) {
          needsDownload = forceUpdate;
        }
      }
    } catch {
      // folder doesn't exist
    }

    let resVersion: CpVersionInfo | undefined;
    if (!forceUpdate) {
      try {
        resVersion = await this.getVersion();
        needsDownload = false;
        if (resVersion.apiVersion != resVersion.cliVersion) {
          needsDownload = await askUserForUpdatePipe(
            this.cpExtConfig,
            resVersion,
            this.logger,
          );
        }
      } catch (err) {
        if (err instanceof CpTokenExpiredError) {
          needsDownload = false;
        } else if (err instanceof UserCancelledError) {
          throw err;
        } else if (err instanceof DependentAbortError) {
          throw err;
        } else {
          vscode.window.showWarningMessage(
            `${this.cpExtConfig.prefix}: Failed to initialize ${this.cpExtConfig.prefix} client: ${err}. Re-downloading...`,
          );
          needsDownload = true;
          resVersion = this.resetVersion();
        }
      }
    }

    if (needsDownload) {
      // Create abort controllers for mutual cancellation
      const downloadAbortController = new AbortController();
      const cleanupAbortController = new AbortController();

      let cleanPipeExecProcessesP = Promise.resolve();
      if (process.platform === "win32") {
        cleanPipeExecProcessesP = this.cleanPipeExecProcesses(
          cleanupAbortController.signal,
        ).catch((err) => {
          // If cleanup was cancelled, abort the download
          if (err instanceof UserCancelledError) {
            this.logger.debug("Cleanup process cancelled by user.");
            downloadAbortController.abort(err);
          }
          throw err;
        });
      }

      await downloadAndExtract(pipeUri, binPipeDir, this.cpExtConfig, {
        abortSignal: downloadAbortController.signal,
        onDidDownload: async () => {
          await cleanPipeExecProcessesP;
          this.resetVersion();
        },
      }).catch((err) => {
        if (err instanceof UserCancelledError) {
          this.logger.debug("Download cancelled by user");
          cleanupAbortController.abort(err);
        }
        throw err;
      });

      resVersion = await this.getVersion();
    }

    if (!resVersion) throw new Error(`Unexpected version '${resVersion}'.`);
    return resVersion;
  }

  private async cleanPipeExecProcesses(
    abortSignal?: AbortSignal,
  ): Promise<void> {
    const tunnels = await this.getExecTunnelList();
    if (!tunnels.length) return;

    // const detail = tunnels
    //   .map(
    //     (t) => `• host: ${t.host}, local port: ${t.localPort}, pid: ${t.pid}`,
    //   )
    //   .join("\n");
    const detail = `host/localPort: ${tunnels.map((t) => `${t.host}/${t.localPort}`).join(", ")}`;

    const stopLabel = "Stop";
    const input = quickPickWithCountdown(
      `${this.cpExtConfig.prefix}: Existing pipe client tunnels must be stopped before updating:${detail}`,
      [
        { label: "Cancel", action: () => "Cancel" },
        { label: stopLabel, action: () => stopLabel },
      ] as ActionQuickPickItem<string>[],
      15000,
    );
    let abortHandler!: (err: any) => void;
    try {
      // Handle abort signal to dispose the input
      const abort = (err: Error) => {
        input.dispose();
        throw err;
      };
      abortSignal?.addEventListener(
        "abort",
        (abortHandler = () => {
          abort(new DependentAbortError("Cleanup aborted dependent"));
        }),
      );

      if ((await input.result).action() !== stopLabel) {
        throw new UserCancelledError("User declined to stop running tunnels");
      }

      const config = await this.cpExtConfig.getClientConfig();
      const env = config ? this.configToEnv(config) : undefined;
      await this.stopExecTunnels(tunnels, env);
    } finally {
      abortSignal?.removeEventListener("abort", abortHandler);
      input.dispose();
    }
  }

  private async stopExecTunnels(
    tunnels: ExecPipeTunnelInfo[],
    env?: NodeJS.ProcessEnv,
  ): Promise<void> {
    await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: `${this.cpExtConfig.prefix}: Stopping running tunnels...`,
        cancellable: false,
      },
      async (progress) => {
        const total = tunnels.length;
        let finished = 0;

        const update = () => {
          progress.report({ message: `${finished}/${total} stopped` });
        };

        update();

        await Promise.all(
          tunnels.map(async (t) => {
            await this.stopExecTunnel(t, env);
            finished++;
            update();
          }),
        );
      },
    );
  }

  private async stopExecTunnel(
    tunnel: ExecPipeTunnelInfo,
    env?: NodeJS.ProcessEnv,
  ): Promise<void> {
    const pipeTunnelStopSuccess = await new Promise<boolean>((resolve) => {
      const child = cp.spawn(
        this.pipeExec,
        ["tunnel", "stop", "-lp", `${tunnel.localPort}`],
        { env },
      );

      child.on("close", (code) => {
        // code != 0 on any reason (including if process not found)
        resolve(code === 0);
      });
    });

    await waitForProcessExit(tunnel.pid, {
      kill: !pipeTunnelStopSuccess,
      timeoutMsg: `${this.cpExtConfig.prefix}: Tunnel process ${tunnel.pid} did not exit in time`,
    });
  }
}

function getBinDir(cpExtConfig: ICpExtConfig): string {
  return path.join(
    cpExtConfig.globalStoragePath /* extension storage */,
    "bin",
  );
}

async function downloadAndExtract(
  pipeUri: string,
  binPipeDir: string,
  cpExtConfig: ICpExtConfig,
  options?: {
    abortSignal?: AbortSignal;
    onDidDownload?: () => Promise<void>;
    onAbort?: () => void;
    logger?: ILogger;
  },
): Promise<void> {
  const fsp = fs.promises;

  // ensure directory exists
  await fsp.mkdir(binPipeDir, { recursive: true });

  // download to a temp file
  const downloadFileName = pipeUri.split("/").slice(-1)[0].split("?")[0];
  const tmpFile = tmp.fileSync({ postfix: "-" + downloadFileName });

  // Handle abort signal to notify caller
  const abortHandler = () => {
    options?.logger?.debug("Download aborted outside.");
    options?.onAbort?.();
  };
  options?.abortSignal?.addEventListener("abort", abortHandler);

  try {
    let downloadFileP: Promise<void>;
    if (process.env.CP_STUBS?.includes("downloads")) {
      // Coppy file from ~/Downloads
      const userHome = process.env.HOME || process.env.USERPROFILE || "";
      const downloadFilePath = path.join(
        userHome,
        "Downloads",
        downloadFileName,
      );
      downloadFileP = fsp.copyFile(downloadFilePath, tmpFile.name);
    } else {
      const pipeUrl = vscode.Uri.parse(cpExtConfig.platformUrl)
        .with({ path: pipeUri })
        .toString();
      downloadFileP = downloadFile(pipeUrl, tmpFile.name, {
        title: `${cpExtConfig.prefix}: Downloading pipe client '${pipeUrl}'`,
        abortSignal: options?.abortSignal,
        logger: options?.logger,
      });
    }

    await downloadFileP;

    const cleanupBinPipeDirP = rimraf(binPipeDir);
    const cleanupRes = await cleanupBinPipeDirP;
    if (!cleanupRes) {
      throw new Error("Failed to clean up existing pipe client dir.");
    }

    const binDir = path.join(binPipeDir, "..");
    await options?.onDidDownload?.();
    try {
      // extract archive
      if (pipeUri.endsWith(".zip")) {
        await unzipperFile(
          tmpFile.name,
          binDir,
          `${cpExtConfig.prefix}: Unzipping pipe client`,
        );
      } else if (pipeUri.endsWith(".tar.gz")) {
        await untarFile(
          tmpFile.name,
          binDir,
          `${cpExtConfig.prefix}: Untarring pipe client`,
        );
      } else {
        throw new Error(`Unsupported archive format "${tmpFile.name}".`);
      }
    } finally {
      tmpFile.removeCallback();
    }
  } finally {
    options?.abortSignal?.removeEventListener("abort", abortHandler);
  }
}
