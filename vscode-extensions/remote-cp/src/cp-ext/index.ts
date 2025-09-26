import * as vscode from "vscode";
import * as path from "path";
import * as fs from "fs";
import * as tar from "tar";
import tmp from "tmp";
import { rimraf } from "rimraf";

import { registerHostTreeView } from "../hostTreeView";
import { registerAuthResolver } from "../authResolver";
import { ILogger } from "../common/logger";
import { CloudPipelineClient } from "../cp-client";
import { CpConfig } from "../config";
import { downloadFile } from "../common/files/downloadFile";
import { Disposable } from "../common/disposable";
import { unzipperFile } from "../common/files/unzipperFile";

// export interface ICpConfig {
//   platformUrl: string;
// }

export enum PipePlatform {
  Linux = "Linux",
  Windows = "Windows",
  MacOS = "MacOS",
  MacOS_ARM = "MacOS-ARM",
}

function getPipePlatform(): PipePlatform {
  if (process.platform === "linux") {
    return PipePlatform.Linux;
  } else if (process.platform === "win32") {
    return PipePlatform.Windows;
  } else if (process.platform === "darwin") {
    if (process.arch === "x64") {
      return PipePlatform.MacOS;
    } else if (process.arch === "arm64") {
      return PipePlatform.MacOS_ARM;
    } else {
      throw new Error(`Unsupported MacOS architecture "${process.arch}".`);
    }
  } else {
    throw new Error(`Unsupported platform "${process.platform}".`);
  }
}

const pipeUris = {
  [PipePlatform.Linux]: { uri: "/pipeline/pipe.tar.gz", exec: "pipe" },
  [PipePlatform.Windows]: { uri: "/pipeline/pipe.zip", exec: "pipe-cli.exe" },
  [PipePlatform.MacOS]: { uri: "/pipeline/pipe-osx.tar.gz", exec: "pipe" },
  [PipePlatform.MacOS_ARM]: {
    uri: "/pipeline/pipe-osx-arm.tar.gz",
    exec: "pipe",
  },
};

export class CpExtension extends Disposable {
  constructor(
    public cpConfig: CpConfig,
    public context: vscode.ExtensionContext,
    public logger: ILogger,
  ) {
    super();
  }

  private cpClient: CloudPipelineClient | null = null;

  async activate(): Promise<void> {
    this.cpClient = await this.activateBinPipe();
    this._register(this.cpClient);

    registerHostTreeView(this.cpClient, this.context, this.logger);
    registerAuthResolver(this.cpClient, this.context, this.logger);
  }

  async activateBinPipe(): Promise<CloudPipelineClient> {
    const fsp = fs.promises;

    const binDir = this.getBinDir();
    const binPipeDir = path.join(binDir, "pipe");
    const pipeExec = path.join(binPipeDir, pipeUris[getPipePlatform()].exec);
    // check if binDir exists and is non-empty
    let needsDownload = true;
    try {
      const stat = await fsp.stat(binPipeDir);
      if (stat.isDirectory()) {
        const files = await fsp.readdir(binPipeDir);
        if (files.length > 0) {
          needsDownload = false;
        }
      }
    } catch {
      // folder doesn't exist
    }

    let cpClient: CloudPipelineClient | null = null;
    if (!needsDownload) {
      try {
        cpClient = new CloudPipelineClient(
          pipeExec,
          this.cpConfig,
          this.logger,
        );
        const v = await cpClient.getVersion();
        needsDownload = false;
        if (v.apiVersion != v.cliVersion) {
          if (
            (await vscode.window.showWarningMessage(
              `${this.cpConfig.prefix} client API vs CLI version mismatch.`,
              "Update",
              "Keep",
            )) === "Update"
          ) {
            if (cpClient) cpClient.dispose();
            cpClient = null;
            needsDownload = true;
          }
        }
      } catch (err) {
        vscode.window.showWarningMessage(
          `${this.cpConfig.prefix} Failed to initialize Cloud Pipeline client: ${err}. Re-downloading...`,
        );
        if (cpClient) cpClient.dispose();
        cpClient = null;
        needsDownload = true;
      }
    }
    if (cpClient) return cpClient;

    vscode.window.showInformationMessage(
      `${this.cpConfig.prefix} PLATFORM_URL: ${this.cpConfig.platformUrl}`,
    );

    // ensure directory exists
    await fsp.mkdir(binPipeDir, { recursive: true });

    // choose URL based on platform
    const platform: PipePlatform = getPipePlatform();
    const pipeUri: string = pipeUris[platform].uri;
    const pipeUrl: string = new URL(
      pipeUri,
      this.cpConfig.platformUrl,
    ).toString();

    // download to a temp file
    const downloadFileName = pipeUri.split("/").slice(-1)[0].split("?")[0];
    const tmpFile = tmp.fileSync({ postfix: "-" + downloadFileName });

    const cleanupBinPipeDirP = rimraf(binPipeDir);
    const downloadFileP = downloadFile(
      pipeUrl,
      tmpFile.name,
      `${this.cpConfig.prefix} Downloading pipe client`,
    );
    await Promise.all([downloadFileP, cleanupBinPipeDirP]);
    try {
      // extract archive
      if (pipeUri.endsWith(".zip")) {
        await unzipperFile(
          tmpFile.name,
          binDir,
          `${this.cpConfig.prefix} Unzipping pipe client`,
        );
      } else if (pipeUri.endsWith(".tar.gz")) {
        await tar.x({ file: tmpFile.name, cwd: binPipeDir });
      }
      cpClient = new CloudPipelineClient(pipeExec, this.cpConfig, this.logger);
    } finally {
      tmpFile.removeCallback();
    }

    return cpClient;
  }

  getBinDir() {
    return path.join(
      this.context.globalStoragePath /* extension storage */,
      "bin",
    );
  }
}
