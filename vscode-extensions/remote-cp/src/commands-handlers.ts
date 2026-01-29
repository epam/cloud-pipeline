import * as vscode from "vscode";
import { getRemoteAuthority } from "./authResolver";
// import { getRemoteAuthority } from './authResolver';
// import { getSSHConfigPath } from './ssh/sshConfig';
// import { exists as fileExists } from './common/files';
// import SSHDestination from './ssh/sshDestination';

export async function promptOpenRemoteCpWindow(_reuseWindow: boolean) {
  const host = await vscode.window.showInputBox({
    title: "Enter [user@]hostname[:port]",
  });

  if (!host) {
    return;
  }

  vscode.window.showWarningMessage("Not implemented yet!");

  // const sshDest = new SSHDestination(host);
  // openRemoteCpWindow(sshDest.toEncodedString(), reuseWindow);
}

export function openRemoteCpWindow(host: string, reuseWindow: boolean) {
  vscode.commands.executeCommand("vscode.newWindow", {
    remoteAuthority: getRemoteAuthority(host),
    reuseWindow,
  });
}

export function openRemoteCpLocationWindow(
  _host: string,
  _path: string,
  _reuseWindow: boolean,
) {
  vscode.window.showWarningMessage("Not implemented yet!");

  // vscode.commands.executeCommand('vscode.openFolder', vscode.Uri.from({ scheme: 'vscode-remote', authority: getRemoteAuthority(host), path }), { forceNewWindow: !reuseWindow });
}

export async function addNewHost() {
  vscode.window.showWarningMessage("Not implemented yet!");

  // const sshConfigPath = getSSHConfigPath();
  // if (!await fileExists(sshConfigPath)) {
  //     await fs.promises.appendFile(sshConfigPath, '');
  // }

  // await vscode.commands.executeCommand('vscode.open', vscode.Uri.file(sshConfigPath), { preview: false });

  // const textEditor = vscode.window.activeTextEditor;
  // if (textEditor?.document.uri.fsPath !== sshConfigPath) {
  //     return;
  // }

  // const textDocument = textEditor.document;
  // const lastLine = textDocument.lineAt(textDocument.lineCount - 1);

  // if (!lastLine.isEmptyOrWhitespace) {
  //     await textEditor.edit((editBuilder: vscode.TextEditorEdit) => {
  //         editBuilder.insert(lastLine.range.end, '\n');
  //     });
  // }

  // let snippet = '\nHost ${1:dev}\n\tHostName ${2:dev.example.com}\n\tUser ${3:john}';
  // await textEditor.insertSnippet(
  //     new vscode.SnippetString(snippet),
  //     new vscode.Position(textDocument.lineCount, 0)
  // );
}

export async function openSSHConfigFile() {
  vscode.window.showWarningMessage("Not implemented yet!");

  // const sshConfigPath = getSSHConfigPath();
  // if (!await fileExists(sshConfigPath)) {
  //     await fs.promises.appendFile(sshConfigPath, '');
  // }
  // vscode.commands.executeCommand('vscode.open', vscode.Uri.file(sshConfigPath));
}
