import * as vscode from "vscode";

declare const BUILTIN_CP_PLATFORM_URL: string;

export interface ICpConfig {
  platformUrl: string;
  prefix: string;
}

export class CpConfig implements ICpConfig {
  constructor(private data = vscode.workspace.getConfiguration("remote-cp")) {}

  public get platformUrl(): string {
    const value =
      this.data.get<string>("platformUrl", "") ||
      (process.env.CP_PLATFORM_URL ?? "") ||
      BUILTIN_CP_PLATFORM_URL;
    // remove trailing slashes
    return value.trim().replace(/\/+$/, "");
  }

  public get prefix(): string {
    return this.data.get<string>("prefix", "CP:");
  }

  public async activate(): Promise<void> {
    if (!this.platformUrl) {
      const inputPlatformUrl = await vscode.window.showInputBox({
        title: "Cloud Pipeline platform URL",
        // prompt: "Cloud Pipeline platform URL",
        placeHolder: "https://cora.company.com",
      });
      // const inputPlatformUrl = await vscode.window.showQuickPick(options, {
      //   placeHolder: "Select an option to save",
      // });
      if (!inputPlatformUrl) {
        throw new Error("Cloud Pipeline platform URL is not specified.");
      }
      vscode.workspace
        .getConfiguration("remote-cp")
        .update("platformUrl", inputPlatformUrl, true);
      this.data = vscode.workspace.getConfiguration("remote-cp");
    }
  }
}
