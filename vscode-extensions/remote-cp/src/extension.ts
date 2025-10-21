// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from "vscode";
import path from "path";
import { DateTime } from "luxon";

import { FileLogger, OutputLogger } from "./common/logger";
import { CpExtension } from "./cp-ext";
import { CpExtConfig } from "./config";

let cpExtConfig: CpExtConfig;
let logger: OutputLogger;
let cpExt: CpExtension | null = null;

// This method is called once when the extension is activated
export async function activate(
  context: vscode.ExtensionContext,
): Promise<void> {
  console.log("Extension 'remote-cp' activating...");

  const helperExt = vscode.extensions.getExtension("epam.remote-cp-helper");
  if (!helperExt) {
    await vscode.window.showErrorMessage(
      "Extension 'remote-cp' requires 'remote-cp-helper' extension to be installed.",
    );
    return;
  }

  cpExtConfig = new CpExtConfig(context);

  const now = DateTime.now();
  const loggerFilePath = path.join(
    cpExtConfig.globalStoragePath,
    "logs",
    `remote-cp.${now.toFormat("yyyyMMdd_HHmmss")}.log`,
  );
  const fileLogger = new FileLogger(
    loggerFilePath.toString(),
    cpExtConfig.logLevel,
    { flags: "w" },
  );
  logger = new OutputLogger("Cloud Pipeline", cpExtConfig.logLevel, fileLogger);
  const msgStart = "Extension 'remote-cp' activating...";
  logger.info(msgStart);
  console.log(msgStart);
  logger.info("Logger\n" + `  file: ${loggerFilePath}`);
  context.subscriptions.push(logger);
  try {
    await cpExtConfig.activate(logger);

    cpExt = new CpExtension(cpExtConfig, context, logger);
    await cpExt.activate();

    const msgEnd = "Extension 'remote-cp' activated.";
    logger.info(msgEnd);
    console.log(msgEnd);
  } catch (err: unknown) {
    if (err instanceof Error) {
      logger.error(`Extension 'remote-cp' activation failed:\n${err.stack}`);
      vscode.window.showErrorMessage(
        "Extension 'remote-cp' activation failed:" +
          `\n  ${err.message}` +
          "\nSee Cloud Pipeline output (logger) for more details.",
      );
    } else {
      logger.error(`Extension 'remote-cp' activation failed:\n${err}`);
    }
  }
}

// This method is called when your extension is deactivated
export async function deactivate(): Promise<void> {
  const msgStart = 'Extension "remote-cp" deactivating...';
  logger.info(msgStart);
  console.log(msgStart);

  const msg2 = 'Extension "remote-cp" continue deactivating ...';
  logger.info(msg2);
  console.log(msg2);

  await cpExt!.dispose();

  const msgEnd = 'Extension "remote-cp" deactivated.';
  logger.info(msgEnd);
  console.log(msgEnd);

  logger.dispose();
}
