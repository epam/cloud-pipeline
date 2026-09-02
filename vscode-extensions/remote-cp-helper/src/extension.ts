import * as vscode from "vscode";

export async function activate(
  _context: vscode.ExtensionContext,
): Promise<void> {
  console.log("Extension 'remote-cp-helper' activating...");
  await patchProductJson();
  console.log("Extension 'remote-cp-helper' activated.");
}

// This method is called when your extension is deactivated
export async function deactivate(): Promise<void> {
  console.log("Extensiobn 'remote-cp-helper' deactivated.");
}

async function patchProductJson() {
  console.log("Patching product.json for 'epam.remote-cp'...");

  const productJsonUri = vscode.Uri.joinPath(
    vscode.Uri.file(vscode.env.appRoot),
    "product.json",
  );

  const obj = JSON.parse(
    new TextDecoder("utf-8").decode(
      await vscode.workspace.fs.readFile(productJsonUri),
    ),
  );

  const objEextensionEnabledApiProposals =
    obj["extensionEnabledApiProposals"] ?? {};
  objEextensionEnabledApiProposals["epam.remote-cp"] = [
    "resolvers",
    "tunnels",
    "contribViewsRemote",
  ];
  obj["extensionEnabledApiProposals"] = objEextensionEnabledApiProposals;

  await vscode.workspace.fs.writeFile(
    productJsonUri,
    new TextEncoder().encode(JSON.stringify(obj, undefined, 2)),
  );
}
