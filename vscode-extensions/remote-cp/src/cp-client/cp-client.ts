import * as vscode from "vscode";
import * as fs from "fs";
import tmp from "tmp";
import { rimraf } from "rimraf";
import * as path from "path";

import { getPipePlatform, PipePlatform, pipeUris } from "./pipe-uris";
import { downloadFile } from "../common/files/downloadFile";
import { unzipperFile } from "../common/files/unzipperFile";
import { untarFile } from "../common/files/untarFile";
import { ICpExtConfig } from "../config";
import { CpClientBase } from "./index";
import { ILogger } from "../common/logger";
import { CpTokenExpiredError } from "./error";

export class CpClient extends CpClientBase {
  static createAndRegister(
    cpExtConfig: ICpExtConfig,
    context: vscode.ExtensionContext,
    logger: ILogger,
  ): CpClient {
    const res = new CpClient(cpExtConfig, logger);
    context.subscriptions.push(res);
    return res;
  }

  protected override async ensurePipeExecInternal(): Promise<void> {
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
          needsDownload = false;
        }
      }
    } catch {
      // folder doesn't exist
    }

    if (!needsDownload) {
      try {
        const v = await this.getVersion();
        needsDownload = false;
        if (v.apiVersion != v.cliVersion) {
          if (
            (await vscode.window.showWarningMessage(
              `${this.cpExtConfig.prefix}: client API ${v.apiVersion} vs CLI ${v.cliVersion} version mismatch.`,
              "Update",
              "Keep",
            )) === "Update"
          ) {
            needsDownload = true;
          }
        }
      } catch (err) {
        if (err instanceof CpTokenExpiredError) {
          needsDownload = false;
        } else {
          vscode.window.showWarningMessage(
            `${this.cpExtConfig.prefix}: Failed to initialize ${this.cpExtConfig.prefix} client: ${err}. Re-downloading...`,
          );
          needsDownload = true;
        }
      }
    }

    if (needsDownload) {
      await downloadAndExtract(pipeUri, binPipeDir, this.cpExtConfig);
    }
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
): Promise<void> {
  const fsp = fs.promises;

  // ensure directory exists
  await fsp.mkdir(binPipeDir, { recursive: true });

  // download to a temp file
  const downloadFileName = pipeUri.split("/").slice(-1)[0].split("?")[0];
  const tmpFile = tmp.fileSync({ postfix: "-" + downloadFileName });

  const cleanupBinPipeDirP = rimraf(binPipeDir);
  let downloadFileP: Promise<void>;
  if (process.env.CP_STUBS === "downloads") {
    // Coppy file from ~/Downloads
    const userHome = process.env.HOME || process.env.USERPROFILE || "";
    const downloadFilePath = path.join(userHome, "Downloads", downloadFileName);
    downloadFileP = fsp.copyFile(downloadFilePath, tmpFile.name);
  } else {
    const pipeUrl = vscode.Uri.parse(cpExtConfig.platformUrl)
      .with({ path: pipeUri })
      .toString();
    downloadFileP = downloadFile(
      pipeUrl,
      tmpFile.name,
      `${cpExtConfig.prefix}: Downloading pipe client '${pipeUrl}'`,
    );
  }
  await Promise.all([downloadFileP, cleanupBinPipeDirP]);
  const binDir = path.join(binPipeDir, "..");
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
}
