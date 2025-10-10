import * as vscode from "vscode";

import { registerHostTreeView } from "../hostTreeView";
import { ILogger } from "../common/logger";
import { CpExtConfig } from "../config";
import { Disposable } from "../common/disposable";
import { CpClient } from "../cp-client/cp-client";
import { RemoteCpResolver } from "../authResolver";
import { REMOTE_CP_AUTHORITY } from "../authResolver.backuo";

export class CpExtension extends Disposable {
  constructor(
    public cpExtConfig: CpExtConfig,
    public context: vscode.ExtensionContext,
    public logger: ILogger,
  ) {
    super();
  }

  // private cpAuthProvider: vscode.AuthenticationProvider | null = null;
  private cpClient: CpClient | null = null;

  // prettier-ignore
  async activate(): Promise<void> {
    // this.cpAuthProvider = await this.registerAuthProvider();
    this.cpClient = await this.registerCpClient();
    this._register(this.cpClient);

    registerUriHandler(this.context, this.logger);

    if (vscode.env.remoteName === REMOTE_CP_AUTHORITY) {
      this.logger.warn(`Remote environment detected. Ensuring configuration...`);
      await this.cpClient.ensureConfig(true);
    }

    registerHostTreeView(this.cpClient, this.context, this.logger);
    RemoteCpResolver.createAndRegister(this.cpClient, this.context, this.logger);
  }

  // async registerAuthProvider(): Promise<CpAuthProvider> {
  //   return CpAuthProvider.createAndRegister(
  //     this.cpExtConfig,
  //     this.context,
  //     this.logger,
  //   );
  // }

  async registerCpClient(): Promise<CpClient> {
    return CpClient.createAndRegister(
      this.cpExtConfig,
      this.context,
      this.logger,
    );
  }
}
function registerUriHandler(context: vscode.ExtensionContext, logger: ILogger) {
  logger.info(
    `Register URI handler scheme: '${vscode.env.uriScheme}://${"epam.remote-cp"}'...`,
  );
  context.subscriptions.push(
    vscode.window.registerUriHandler({
      handleUri: async (uri: vscode.Uri) => {
        // uri example: vscode://epam.remote-cp/{runId}/{path}
        const uriParts = uri.path.split("/");

        const runHost = uriParts[1];
        const path = "/" + uriParts.slice(2).join("/");
        // const run: RunInfo = new RunInfo(runId);

        const openUri = vscode.Uri.from({
          scheme: "vscode-remote",
          authority: `cp-remote+${runHost}`,
          path: path,
        });
        const openUri2 = vscode.Uri.parse(
          "vscode-remote://cp-remote+pipeline-77805/root/projs/test-1",
        );

        vscode.commands.executeCommand("vscode.openFolder", openUri, {
          forceNewWindow: false,
        });
      },
    }),
  );
}
