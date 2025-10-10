// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from "vscode";

import { Logger } from "./common/logger";
import { CpExtension } from "./cp-ext";
import { CpExtConfig } from "./config";

let cpExtConfig: CpExtConfig;
let logger: Logger;
let ext: CpExtension | null = null;

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
  logger = new Logger("Cloud Pipeline", cpExtConfig.logLevel);
  logger.info('Extension "remote-cp" activating...');
  context.subscriptions.push(logger);
  try {
    await cpExtConfig.activate(logger);

    ext = new CpExtension(cpExtConfig, context, logger);
    await ext.activate();
    context.subscriptions.push(ext);

    logger.info('Extension "remote-cp" activated.');
    console.log("Extension 'remote-cp' activated.");
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
  await ext?.dispose();
  console.log("Extensiobn 'remote-cp' is deactivated");
}
