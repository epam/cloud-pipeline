import * as vscode from "vscode";

import { registerHostTreeView } from "../cp-run-view/register";
import { ILogger } from "../common/logger";
import { CpExtConfig } from "../config";
import { Disposable } from "../common/disposable";
import { CpClient } from "../cp-client/cp-client";
import { REMOTE_CP_AUTHORITY, RemoteCpResolver } from "../authResolver";
import { OnStartOption, OnStartWhen } from "./on-start";
import { PipeTunnelInfo } from "../cp-client";
import { CpCodeContext } from "./code-context";

export class CpExtension extends Disposable {
  private static objCounter = 0;
  private objId = CpExtension.objCounter++;

  protected toLog(): string {
    return `${this.constructor.name}<${this.objId}>`;
  }

  constructor(
    public cpExtConfig: CpExtConfig,
    public context: vscode.ExtensionContext,
    public codeContext: CpCodeContext,
    public logger: ILogger,
  ) {
    super();
  }

  // private cpAuthProvider: vscode.AuthenticationProvider | null = null;
  private _cpClient: CpClient | null = null;
  public get cpClient(): CpClient {
    return this._cpClient!;
  }

  // prettier-ignore
  async activate(): Promise<void> {
    // this.cpAuthProvider = await this.registerAuthProvider();
    this._cpClient = await this.registerCpClient();
    this._register(this.cpClient);

    RemoteCpResolver.createAndRegister(this);
    registerUriHandler(this.context, this.logger);

    if (vscode.env.remoteName === REMOTE_CP_AUTHORITY) {
      this.logger.warn(`Remote environment detected. Ensuring configuration...`);
      await this.cpClient.ensureConfig(true);
    }

    registerHostTreeView(this);

    vscode.workspace.onDidChangeWorkspaceFolders(async (_event) => {
      this.logger.info(`Workspace folders changed.`);
    });
  }

  private async scanOnStart<TArgs>(
    when: OnStartWhen,
    args?: TArgs,
  ): Promise<any> {
    const onStartList = [...(await this.cpExtConfig.getOnStart())];
    let offset = 0;
    for (let i = 0; i < onStartList.length; i++) {
      const onStartProps = onStartList[i];
      if (onStartProps.when === when) {
        try {
          const onStartOption = new OnStartOption(onStartProps);
          return await onStartOption.run(this, args);
        } finally {
          const onStartV = await this.cpExtConfig.getOnStart();
          const resOnStart = onStartV.filter((_, idx) => idx != i - offset);
          this.cpExtConfig.setOnStart(resOnStart);
          offset++;
        }
      }
    }
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
      this.cpExtConfig as any,
      this.context,
      this.codeContext,
      this.logger,
    );
  }

  // -- Reuse pipe tunnel --

  public async getReusePipeTunnel(): Promise<PipeTunnelInfo | null> {
    const logPfx = `${this.toLog()}.getReusePipeTunnel()`;
    let res: PipeTunnelInfo | null =
      ((await this.scanOnStart(OnStartWhen.onWillResolve)) as PipeTunnelInfo) ||
      null;
    if (res) {
      this.logger.debug(
        `${logPfx}, onWillResolve callback, suggested pipe tunnel to reuse:\n` +
          `  pid: localPort: ${res.localPort}, runId: ${res.runId}, pid: ${res.pid}`,
      );
      const tunnelList = await this.cpClient.getTunnelList();
      res = tunnelList.find((ti) => ti.pid === res!.pid) ?? null;
      if (res) {
        this.logger.debug(
          `${logPfx}, suggested pipe tunnel to reuse found, pid: ${res.pid}.`,
        );
      } else {
        void vscode.window.showWarningMessage(
          `${this.cpExtConfig.prefix}: suggested pipe tunnel to reuse not found (pid: ${res!.pid}).`,
        );
        this.logger.warn(
          `${logPfx}, suggested pipe tunnel to reuse not found, pid: ${res!.pid}.`,
        );
      }
    } else {
      this.logger.debug(`${logPfx}, no suggested pipe tunnel to reuse.`);
    }
    return res;
  }

  public async setReusePipeTunnel(tunnelInfo: PipeTunnelInfo | null) {
    await this.scanOnStart(OnStartWhen.onDidResolve, tunnelInfo);
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
        vscode.commands.executeCommand("vscode.openFolder", openUri, {
          forceNewWindow: false,
        });
      },
    }),
  );
}
