import * as vscode from "vscode";
import { RemoteLocationHistory } from "../remoteLocationHistory";
import { CpRunTreeDataProvider, ItemInfo } from "./data-provider";
import { CpRunViewProvider } from "./view-provider";
import { CpExtension } from "../cp-ext";

export function registerHostTreeView(cpExt: CpExtension): void {
  const locationHistory = new RemoteLocationHistory(cpExt.context);
  const hostTreeDataProvider = new CpRunTreeDataProvider(
    cpExt,
    locationHistory,
    cpExt.logger,
  );

  const cpRunViewProvider = new CpRunViewProvider<ItemInfo>(
    cpExt,
    hostTreeDataProvider,
  );

  //cpExt.context.subscriptions.push(
  vscode.window.registerWebviewViewProvider(
    "cpRunView",
    cpRunViewProvider,
    /* , { webviewOptions: { retainContextWhenHidden: true }, */
  );
  //);
}
