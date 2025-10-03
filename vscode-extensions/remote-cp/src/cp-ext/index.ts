import * as vscode from "vscode";

import { registerHostTreeView } from "../hostTreeView";
import { registerAuthResolver } from "../authResolver";
import { ILogger } from "../common/logger";
import { CpExtConfig } from "../config";
import { Disposable } from "../common/disposable";
import { CpAuthProvider } from "./auth-provider";
import { CpClient } from "../cp-client/cp-client";

export class CpExtension extends Disposable {
  constructor(
    public cpExtConfig: CpExtConfig,
    public context: vscode.ExtensionContext,
    public logger: ILogger,
  ) {
    super();
  }

  private cpAuthProvider: vscode.AuthenticationProvider | null = null;
  private cpClient: CpClient | null = null;

  async activate(): Promise<void> {
    this.cpAuthProvider = await this.registerAuthProvider();
    this.cpClient = await this.registerCpClient();
    this._register(this.cpClient);

    registerHostTreeView(this.cpClient, this.context, this.logger);
    registerAuthResolver(this.cpClient, this.context, this.logger);
  }

  async registerAuthProvider(): Promise<CpAuthProvider> {
    return CpAuthProvider.createAndRegister(
      this.cpExtConfig,
      this.context,
      this.logger,
    );
  }

  async registerCpClient(): Promise<CpClient> {
    return CpClient.createAndRegister(
      this.cpExtConfig,
      this.context,
      this.logger,
    );
  }
}
