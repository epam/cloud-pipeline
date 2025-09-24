// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from "vscode";
import { Logger } from "./common/logger";
import { registerHostTreeView } from "./hostTreeView";
import { registerAuthResolver } from "./authResolver";
import { CloudPipelineClient } from "./cp-client";

let cpClient: CloudPipelineClient;
let logger: Logger;

// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {
  logger = new Logger("Cloud Pipeline");
  logger.info('Extension "remote-cp" is activated');

  cpClient = new CloudPipelineClient(logger);
  context.subscriptions.push(cpClient, logger);

  registerHostTreeView(cpClient, context, logger);

  registerAuthResolver(cpClient, context, logger);

  // Use the console to output diagnostic information (console.log) and errors (console.error)
  // This line of code will only be executed once when your extension is activated
  logger.info('Congratulations, your extension "remote-cp" is now active!');

  // The command has been defined in the package.json file
  // Now provide the implementation of the command with registerCommand
  // The commandId parameter must match the command field in package.json
  const disposable = vscode.commands.registerCommand(
    "remote-cp.helloWorld",
    () => {
      // The code you place here will be executed every time your command is executed
      // Display a message box to the user
      vscode.window.showInformationMessage(
        "Hello World from Cloud Pipeline Remote!",
      );
    },
  );

  context.subscriptions.push(disposable);
}

// This method is called when your extension is deactivated
export function deactivate() {
  cpClient.dispose();
  console.log("Extensiobn 'remote-cp' is deactivated");
}
