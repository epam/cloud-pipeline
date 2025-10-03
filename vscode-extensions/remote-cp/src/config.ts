import * as vscode from "vscode";
import { ICpClientConfig } from "./cp-client/cp-client-config";
import { ILogger } from "./common/logger";

declare const BUILTIN_CP_PLATFORM_URL: string;

export interface ICpExtConfig {
  getClientConfig(): Promise<ICpClientConfig | null>;
  setClientConfig(resConfig: ICpClientConfig | null): Promise<void>;

  readonly globalStoragePath: string;
  readonly platformUrl: string;
  readonly prefix: string;
  readonly apiEndpoint: string;
  readonly authEndpoint: string;

  setPlatformUrl(value: string): Promise<void>;
}

export const CpExtConfigKeys = {
  platformUrl: "platformUrl",
  prefix: "prefix",
  api: {
    endpoint: "api.endpoint",
  },
  auth: {
    endpoint: "auth.endpoint",
  },

  pipe: {
    apiUri: "pipe.apiUri",
    apiToken: "pipe.apiToken",
  },
};

export class CpExtConfig implements ICpExtConfig {
  private data!: vscode.WorkspaceConfiguration;

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly logger: ILogger,
  ) {
    this.update();
  }

  /**
   * Updates {@link data} object extension settings
   */
  protected update(): void {
    this.data = vscode.workspace.getConfiguration("remote-cp");
  }

  public get globalStoragePath(): string {
    return this.context.globalStoragePath;
  }

  public get platformUrl(): string {
    const value =
      this.data.get<string>(CpExtConfigKeys.platformUrl, "") ||
      (process.env.CP_PLATFORM_URL ?? "") ||
      BUILTIN_CP_PLATFORM_URL;
    // remove trailing slashes
    return value.trim().replace(/\/+$/, "");
  }

  public get prefix(): string {
    return this.data.get<string>(CpExtConfigKeys.prefix, "CP:");
  }

  public get apiEndpoint(): string {
    return this.data.get<string>(
      CpExtConfigKeys.api.endpoint,
      process.env.CP_API_ENDPOINT || "",
    );
  }

  public get authEndpoint(): string {
    return this.data.get<string>(
      CpExtConfigKeys.auth.endpoint,
      process.env.CP_AUTH_ENDPOINT || "",
    );
  }

  public async setPlatformUrl(value: string): Promise<void> {
    return this.data.update(CpExtConfigKeys.platformUrl, value, true);
  }

  public async getClientConfig(): Promise<ICpClientConfig | null> {
    let apiUri = this.data.get<string>(CpExtConfigKeys.pipe.apiUri);
    if (!apiUri)
      apiUri = vscode.Uri.parse(this.platformUrl)
        .with({
          path: this.apiEndpoint,
        })
        .toString();
    const apiToken = this.data.get<string>(CpExtConfigKeys.pipe.apiToken);
    if (!apiUri || !apiToken) {
      this.logger.warn("Pipe client configuration not found in extension.");
      return null;
    } else
      return {
        apiUri,
        apiToken,
      };
  }

  // prettier-ignore
  public async setClientConfig(config: ICpClientConfig | null): Promise<void> {
    await this.data.update(CpExtConfigKeys.pipe.apiUri, config?.apiUri, true);
    await this.data.update(CpExtConfigKeys.pipe.apiToken, config?.apiToken, true);
    this.update();
  }

  public async activate(): Promise<void> {
    if (!this.platformUrl) {
      const inputPlatformUrl = await vscode.window.showInputBox({
        title: `${this.prefix} platform URL`,
        // prompt: `${this.prefix} platform URL`,
        placeHolder: "https://cora.company.com",
      });
      // const inputPlatformUrl = await vscode.window.showQuickPick(options, {
      //   placeHolder: "Select an option to save",
      // });
      if (!inputPlatformUrl) {
        throw new Error(`${this.prefix} platform URL is not specified.`);
      }
      vscode.workspace
        .getConfiguration("remote-cp")
        .update(CpExtConfigKeys.platformUrl, inputPlatformUrl, true);
      this.data = vscode.workspace.getConfiguration("remote-cp");
    }
  }
}
