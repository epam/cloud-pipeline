// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from "vscode";

import { Logger } from "./common/logger";
import { CpExtension } from "./cp-ext";
import { CpExtConfig } from "./config";

let ext: CpExtension | null = null;
let logger: Logger;

// This method is called once when the extension is activated
export async function activate(
  context: vscode.ExtensionContext,
): Promise<void> {
  logger = new Logger("Cloud Pipeline");
  logger.info('Extension "remote-cp" activating...');
  context.subscriptions.push(logger);
  try {
    const cpExtConfig = new CpExtConfig(context, logger);
    await cpExtConfig.activate();

    ext = new CpExtension(cpExtConfig, context, logger);
    await ext.activate();
    context.subscriptions.push(ext);

    logger.info('Extension "remote-cp" activated!');
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
