import * as vscode from "vscode";
import { ILogger } from "../common/logger";

export function subscribeAllEvents(
  context: vscode.ExtensionContext,
  logger: ILogger,
): void {
  for (const memberName of Object.keys(vscode.authentication)) {
    if (memberName.startsWith("onDid")) {
      const member = (vscode.authentication as any)[memberName];
      if (typeof member === "function") {
        try {
          context.subscriptions.push(
            (member as any).call(vscode.authentication, () =>
              logger.info(`Event fired 'vscode.authentication.${memberName}'.`),
            ),
          );
        } catch (err) {
          logger.error(
            `Failed to subscribe 'vscode.authentication.${memberName}': ${err}`,
          );
        }
      }
    }
  }

  for (const memberName of Object.keys(vscode.window)) {
    if (
      memberName.startsWith("onDid") &&
      memberName != "onDidChangeTextEditorSelection" &&
      memberName != "onDidChangeTextEditorVisibleRanges" &&
      memberName != "onDidChangeVisibleTextEditors" &&
      memberName != "onDidChangeVisibleTextEditors" &&
      memberName != "onDidChangeTextEditorDiffInformation" &&
      memberName != "onDidChangeTerminalDimensions" &&
      memberName != "onDidExecuteTerminalCommand" &&
      memberName != "onDidChangeWindowState" &&
      memberName != "onDidChangeActiveTextEditor" &&
      memberName != "onDidWriteTerminalData"
    ) {
      const member = (vscode.window as any)[memberName];
      if (typeof member === "function") {
        try {
          context.subscriptions.push(
            (member as any).call(vscode.window, () =>
              logger.info(`Event fired 'vscode.window.${memberName}'.`),
            ),
          );
        } catch (err) {
          logger.error(
            `Failed to subscribe 'vscode.window.${memberName}': ${err}`,
          );
        }
      }
    }
  }

  for (const memberName of Object.keys(vscode.workspace)) {
    if (
      memberName.startsWith("onDid") &&
      memberName != "onDidChangeTextDocument" &&
      memberName != "onDidCloseTextDocument" &&
      memberName != "onDidOpenTextDocument"
    ) {
      const member = (vscode.workspace as any)[memberName];
      if (typeof member === "function") {
        try {
          context.subscriptions.push(
            (member as any).call(vscode.workspace, () =>
              logger.info(`Event fired 'vscode.workspace.${memberName}'.`),
            ),
          );
        } catch (err) {
          logger.error(
            `Failed to subscribe 'vscode.workspace.${memberName}': ${err}`,
          );
        }
      }
    }
  }

  for (const memberName of Object.keys(vscode.env)) {
    if (
      memberName.startsWith("onDid") &&
      memberName != "onDidChangeTelemetryConfiguration"
    ) {
      try {
        const member = (vscode.env as any)[memberName];
        if (typeof member === "function") {
          context.subscriptions.push(
            (member as any).call(vscode.env, () =>
              logger.info(`Event fired 'vscode.env.${memberName}'.`),
            ),
          );
        }
      } catch (err) {
        logger.error(`Failed to subscribe 'vscode.env.${memberName}': ${err}`);
      }
    }
  }
}
