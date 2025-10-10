import * as vscode from "vscode";
import * as cp from "child_process";

import { ILogger } from "../common/logger";
import { pipeParse } from "./pipeParse";
import { PipeTunnel } from "../pipeTunnel";
import { Disposable } from "../common/disposable";
import { ICpExtConfig as ICpExtConfig } from "../config";
import { fileExists, readJsonFile } from "../common/files/file";
import { ICpClientConfig } from "./cp-client-config";
import { CpAuthInvalidError, CpTokenExpiredError } from "../cp-client/error";
import { configureWithCliConfigurationCommand } from "./configure-with-cli-configuration-command";
import { configureWithCpUrl } from "./configure-with-cp-web-auth";
import { configureWithOAuth } from "./configure-with-oauth";

export enum PipeRunCols {
  runId = "RunID",
  parentRunId = "Parent RunID",
  pipeline = "Pipeline",
  version = "Version",
  status = "Status",
  started = "Started",
  owner = "Owner",
}

export class RunInfo {
  public locations?: RunLocation[];

  constructor(
    public runId: number,
    public parentRunId: number | null,
    public pipeline: string,
    public version: string | null,
    public status: string,
    public started: string,
    public owner: string,
  ) {}
}

export class RunLocation {
  constructor(
    public run: RunInfo,
    public path: string,
  ) {}
}

export function pipeParseRunList(table: string): RunInfo[] {
  return pipeParse<RunInfo>(table, (cells, _header: string[]) => {
    const res = new RunInfo(
      /* runId: */ parseInt(cells[0]),
      /* parentRunId: */ cells[1] === "None" ? null : parseInt(cells[1]),
      /* pipeline: */ cells[2],
      /* version: */ cells[3] === "None" ? null : cells[3],
      /* status: */ cells[4],
      /* started: */ cells[5],
      /* owner: */ cells[6],
    );
    return res;
  });
}

export class TunnelInfo {
  constructor(
    public pid: number,
    public parentPid: number | null,
    public owner: string,
    /* Host */ public runId: string,
    public localPort: number,
    public remotePort: number,
  ) {}
}

export function pipeParseTunnelList(table: string): TunnelInfo[] {
  return pipeParse<TunnelInfo>(table, (cells, _header: string[]) => {
    const res = new TunnelInfo(
      /* PID: */ parseInt(cells[0]),
      /* PPID: */ cells[1] === "None" ? null : parseInt(cells[1]),
      /* Owner: */ cells[2],
      /* Host: */ cells[3],
      /* LocalPorts: */ parseInt(cells[4]),
      /* RemotePorts: */ parseInt(cells[5]),
    );
    return res;
  });
}

export class CpVersionInfo {
  constructor(
    public apiVersion: string,
    public apiVersionHash: string,
    public cliVersion: string,
    public cliVersionHash: string,
    public tokenOwner: string,
    public tokenIssuedAt: string,
    public tokenExpiresAt: string,
  ) {}
}

/**
 * pipe-cli
 *   Tries to use {install-dir}/config.json
 *   and fallbacks to {home-dir}/config.json auto and silent
 */
export abstract class CpClientBase extends Disposable {
  private static objCounter = 0;
  private objId = CpClientBase.objCounter++;

  protected toLog(): string {
    return `${this.constructor.name}<${this.objId}>`;
  }

  protected pipeExec!: string;

  protected constructor(
    public readonly cpExtConfig: ICpExtConfig,
    protected readonly logger: ILogger,
  ) {
    super();
  }

  override dispose() {
    if (!this.isDisposed) {
      // FIX: Cleanup resources
    }
    super.dispose();
  }

  async getVersion(): Promise<CpVersionInfo> {
    const output = await this.execPipeCommand("--version");
    const apiM = output.match(
      /^Cloud Pipeline API, version (\d+\.\d+\.\d+\.\d+)\.([0-9a-f]{40})/m,
    );
    const cliM = output.match(
      /^Cloud Pipeline CLI, version (\d+\.\d+\.\d+\.\d+)\.([0-9a-f]{40})/m,
    );
    const tokenM = output.match(
      /^Access token info:\s*\nIssued to: (\w+)\s*\nIssued at: (\d{4}-\d{2}-\d{2} \d{2}:\d{2})\s*\nExpires at: (\d{4}-\d{2}-\d{2} \d{2}:\d{2})/m,
    );
    if (!apiM && cliM && tokenM) {
      throw new CpTokenExpiredError(`Access token is expired: ${tokenM[3]}`);
    } else if (!apiM || !cliM || !tokenM) {
      throw new Error(`Failed to parse 'pipe --version' output: ${output}`);
    }

    // prettier-ignore
    const res = new CpVersionInfo(
      apiM[1], apiM[2],
      cliM[1], cliM[2],
      tokenM[1], tokenM[2], tokenM[3],
    );
    return res;
  }

  /**
   * Gets run list with `pipe view-runs` command
   */
  async getRunList(): Promise<RunInfo[]> {
    const logPfx = `${this.toLog()}.getRunList()`;
    this.logger.trace(`${logPfx}, start`);
    try {
      const output = await this.execPipeCommand(`view-runs`);
      const res = await pipeParseRunList(output);
      this.logger.trace(`${logPfx}, end`);
      return res;
    } finally {
      await this.cpExtConfig.save(logPfx);
      this.logger.trace(`${logPfx}, finally`);
    }
  }

  private async configSpawn(
    ...args: readonly string[]
  ): Promise<cp.ChildProcessWithoutNullStreams> {
    const config = await this.ensureConfig();
    const env = this.configToEnv(config);

    try {
      return cp.spawn(this.pipeExec, args, { env });
    } catch (err) {
      const _m = err.message.match(CpTokenExpiredError.re);
      throw err;
    }
  }

  /**
   * Executes {@link file} with {@link args} and returns output.
   * @returns Output
   */
  private async configExecFile(...args: readonly string[]): Promise<string> {
    const config = await this.ensureConfig();
    const env = this.configToEnv(config);

    return new Promise<string>((resolve, reject) => {
      const dt1 = performance.now();
      cp.execFile(this.pipeExec, args, { env }, (error, stdout, stderr) => {
        const dt2 = performance.now();
        this.logger.debug(
          `Client pipe command exec for ${(dt2 - dt1) / 1000} s`,
        );
        if (error || stderr) {
          const m =
            error?.message.match(CpTokenExpiredError.re) ||
            stderr.match(CpTokenExpiredError.re);
          if (m) {
            reject(new CpTokenExpiredError(m[0]));
          } else {
            reject(error || new Error(stderr));
          }
        } else {
          resolve(stdout);
        }
      });
    });
  }

  /**
   * Calls {@link ensureConfig}
   */
  private async execPipeCommand(...args: readonly string[]): Promise<string> {
    while (true) {
      try {
        return await this.configExecFile(...args);
      } catch (err) {
        if (
          err instanceof CpTokenExpiredError ||
          err instanceof CpAuthInvalidError
        ) {
          await this.cpExtConfig.setClientConfig(null);
          const errUserResp = await vscode.window.showErrorMessage(
            err.message,
            ...["Retry", "Abort"],
          );
          if (errUserResp === "Abort") {
            throw err;
          }
        } else {
          throw err;
        }
      }
    }
  }

  async getTunnelList(): Promise<TunnelInfo[]> {
    const output = await this.execPipeCommand(`${this.pipeExec} tunnel list`);
    return pipeParseTunnelList(output);
  }

  async startTunnel(
    cpRunId: number,
    context: vscode.ExtensionContext,
  ): Promise<PipeTunnel> {
    const resPipeTunnel = new PipeTunnel(
      cpRunId,
      this.cpExtConfig,
      this.logger,
    );
    this._register(resPipeTunnel);
    // context.subscriptions.push(resPipeTunnel);
    await resPipeTunnel.activate(
      (localPort: number): Promise<cp.ChildProcessWithoutNullStreams> => {
        // prettier-ignore
        return this.configSpawn(
          "tunnel", "start", "-f", "--ssh",
          "--ignore-existing",
          // "--no-putty",
          "-rp", "22",
          "-lp", localPort.toString(),
          "--log-level", "INFO",
          cpRunId.toString());
      },
    );

    return resPipeTunnel;
  }

  async stopTunnel(tunnel: PipeTunnel): Promise<void> {
    await tunnel.deactivate();
  }

  private ensurePipeExecActive: boolean = false;

  protected abstract ensurePipeExecInternal(): Promise<void>;

  private async ensurePipeExec(): Promise<void> {
    if (!this.ensurePipeExecActive) {
      this.ensurePipeExecActive = true;
      try {
        return await this.ensurePipeExecInternal();
      } finally {
        this.ensurePipeExecActive = false;
      }
    } else {
      return Promise.resolve();
    }
  }

  // -- Config --
  public async ensureConfig(saveConfig = false): Promise<ICpClientConfig> {
    const logPfx = `${this.toLog()}.ensureConfig()`;

    await this.ensurePipeExec();
    if (!this.pipeExec) throw new Error("Pipe client exec is not configured");

    let resConfig: ICpClientConfig | null;
    resConfig = await this.cpExtConfig.getClientConfig();
    if (resConfig) return resConfig;

    resConfig = await getInstallDirConfigJson(this.pipeExec);
    if (resConfig) return resConfig;

    const configureActions = {
      cliConfigurationCommands: "CLI configuration command",
      cpUrl: `${this.cpExtConfig.prefix} web auth`,
      cpAuth: "${this.cpExtConfig.prefix} OAuth",
    };

    const configUserResp = await vscode.window.showWarningMessage(
      `${this.cpExtConfig.prefix} pipe client is not configured.`,
      ...[
        configureActions.cliConfigurationCommands,
        configureActions.cpUrl,
        "Skip",
      ],
    );

    switch (configUserResp) {
      case configureActions.cliConfigurationCommands: {
        resConfig = await configureWithCliConfigurationCommand(
          this.cpExtConfig,
          this.logger,
        );
        break;
      }
      case configureActions.cpUrl: {
        resConfig = await configureWithCpUrl(this.cpExtConfig, this.logger);
        break;
      }

      case configureActions.cpAuth: {
        resConfig = await configureWithOAuth(this.cpExtConfig, this.logger);
        break;
      }
    }

    if (resConfig) {
      await this.cpExtConfig.setClientConfig(resConfig);
      if (saveConfig) await this.cpExtConfig.save(logPfx);
    } else {
      const errMsg = `${this.cpExtConfig.prefix} pipe client is not configured.`;
      this.logger.error(errMsg);
      throw new Error(errMsg);
    }
    return resConfig;
  }

  // -- routines --

  private configToEnv(config: ICpClientConfig): any {
    const resEnv = {
      API: config.apiUri,
      API_TOKEN: config.apiToken,
    };
    return resEnv;
  }
}

async function getInstallDirConfigJson(
  pipeExec: string,
): Promise<ICpClientConfig | null> {
  const pipeExecUri = vscode.Uri.file(pipeExec);
  const binPipeDir = vscode.Uri.joinPath(pipeExecUri, "..");
  const configJsonFile = vscode.Uri.joinPath(binPipeDir, "config.json");
  const exists: boolean = await fileExists(configJsonFile);
  if (!exists) return null;
  const resConfig = await readJsonFile<ICpClientConfig>(configJsonFile);
  return resConfig;
}
