import * as vscode from "vscode";

export function activateRep(outputChannel: vscode.OutputChannel): void {
  var repObj = new Proxy(
    {},
    {
      get: function (target, prop, receiver): void {
        outputChannel.appendLine(
          `Remote Explorer property '${String(prop)}' is not implemented.`
        );
      },
      set: function (target, prop, value, receiver): boolean {
        return true;
      },
      has: function (target, prop: string): boolean {
        return true;
      },
    }
  );
  // vscode.extensions.registerRemoteExplorerType('remote-cp', repObj);
  console.warn("activateRep");
}
