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
import { PipeTunnelBase } from "./tunnel/pipe-tunnel-base";
import {
  askUserForPipeTunnel,
  ExecutePipeTunnelItem,
  ReusePipeTunnelItem,
} from "./tunnel/ask-user-for-pipe-tunnel";
import { findRandomPort } from "../common/ports";
import { ReusedPipeTunnel } from "./tunnel/reusing-pipe-tunnel";

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

export class PipeTunnelInfo {
  constructor(
    public pid: number,
    public parentPid: number | null,
    public owner: string,
    /* Host */ public runId: number,
    public localPort: number,
    public remotePort: number,
  ) {}
}

export function pipeParseTunnelList(table: string): PipeTunnelInfo[] {
  return pipeParse<PipeTunnelInfo>(table, (cells, _header: string[]) => {
    const res = new PipeTunnelInfo(
      /* PID: */ parseInt(cells[0]),
      /* PPID: */ cells[1] === "None" ? null : parseInt(cells[1]),
      /* Owner: */ cells[2],
      /* Host: */ parseInt(cells[3]),
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
    public readonly logger: ILogger,
  ) {
    super();
  }

  override dispose() {
    if (!this.isDisposed) {
      // FIX: Cleanup resources
    }
    super.dispose();
  }

  // TODO: Reset {@link this.version} on change
  private version: CpVersionInfo | null = null;

  async getVersion(): Promise<CpVersionInfo> {
    if (this.version) return this.version;

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
    return (this.version = res);
  }

  protected resetVersion(): undefined {
    this.version = null;
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

  public async configSpawn(
    args: readonly string[],
    options?: cp.SpawnOptionsWithoutStdio,
  ): Promise<[cp.ChildProcessWithoutNullStreams, CpVersionInfo]> {
    const [config, version] = await this.ensureConfig();
    const env = this.configToEnv(config);

    try {
      const resProcess = cp.spawn(this.pipeExec, args, {
        env: env,
        ...options,
      });
      return [resProcess, version!];
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
    const [config, _version] = await this.ensureConfig();
    const env = this.configToEnv(config);

    return new Promise<string>((resolve, reject) => {
      const dt1 = performance.now();
      cp.execFile(this.pipeExec, args, { env }, (error, stdout, stderr) => {
        const dt2 = performance.now();
        this.logger.debug(
          `Pipe client command exec for ${(dt2 - dt1) / 1000} s`,
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

  async getTunnelList(): Promise<PipeTunnelInfo[]> {
    const output = await this.execPipeCommand("tunnel", "list");
    return pipeParseTunnelList(output);
  }

  async startTunnel(
    cpRunId: number,
    reuseTunnel: PipeTunnelInfo | null,
  ): Promise<PipeTunnelBase> {
    const logPfx = `${this.toLog()}.startTunnel()`;
    let resPipeTunnel: PipeTunnelBase | undefined;

    if (reuseTunnel) {
      resPipeTunnel = new ReusedPipeTunnel(reuseTunnel);
    }

    if (!resPipeTunnel) {
      const runTunnelList = (await this.getTunnelList()).filter(
        (ti) => ti.runId === cpRunId,
      );

      // let resPipeTunnel: PipeTunnelBase | undefined;
      const pipeTunnelUserResp = await askUserForPipeTunnel(
        cpRunId,
        runTunnelList,
      );

      if (pipeTunnelUserResp instanceof ReusePipeTunnelItem) {
        resPipeTunnel = new ReusedPipeTunnel(pipeTunnelUserResp.tunnelInfo);
      } else if (pipeTunnelUserResp instanceof ExecutePipeTunnelItem) {
        this.logger.trace(`${logPfx}, user resp to execute pipe tunnel`);
        resPipeTunnel = await (async (): Promise<PipeTunnelBase> => {
          const logPfx2 = `${logPfx}.execPipeTunnel`;
          this.logger.trace(`${logPfx2}, start`);
          const localPort = await findRandomPort();
          this.logger.trace(`${logPfx2}, localPort: ${localPort}`);
          const res = new PipeTunnel(
            cpRunId,
            localPort,
            pipeTunnelUserResp.toStop,
            this.cpExtConfig,
            this.logger,
          );
          this.logger.trace(`${logPfx2}, created`);
          // context.subscriptions.push(resPipeTunnel);
          await res.activate(
            async (
              localPort: number,
              toStop: boolean,
            ): Promise<[cp.ChildProcessWithoutNullStreams, CpVersionInfo]> => {
              const logPfx3 = `${logPfx2}.startProcess`;
              this.logger.trace(`${logPfx3}, start`);
              const [resProcess, resVersion] = await this.configSpawn(
                // prettier-ignore
                [
                  "tunnel", "start", "-f", "--ssh",
                  "--ignore-existing",
                  // "--no-putty",
                  "-rp", "22",
                  "-lp", localPort.toString(),
                  "--log-level", "INFO",
                  cpRunId.toString()
                ],
                { detached: !toStop },
              );
              this.logger.trace(`${logPfx3}, end (spawned)`);
              return [resProcess, resVersion];
            },
          );
          this.logger.trace(`${logPfx2}, end (activated)`);
          return res;
        })();
      }
    }
    this._register(resPipeTunnel!);
    return resPipeTunnel!;
  }

  async stopTunnel(tunnel: PipeTunnel): Promise<void> {
    await tunnel.deactivate();
  }

  private ensurePipeExecActive: boolean = false;

  public abstract ensurePipeExec(forceUpdate?: boolean): Promise<CpVersionInfo>;

  private async ensurePipeExecInternal(): Promise<CpVersionInfo | null> {
    if (!this.ensurePipeExecActive) {
      this.ensurePipeExecActive = true;
      try {
        return await this.ensurePipeExec();
      } finally {
        this.ensurePipeExecActive = false;
      }
    } else {
      return null;
    }
  }

  // -- Config --
  public async ensureConfig(
    saveConfig = false,
  ): Promise<[ICpClientConfig, CpVersionInfo | null]> {
    const logPfx = `${this.toLog()}.ensureConfig()`;

    const resVersion = await this.ensurePipeExecInternal();
    if (!this.pipeExec) throw new Error("Pipe client exec is not configured");

    let resConfig: ICpClientConfig | null;
    resConfig = await this.cpExtConfig.getClientConfig();
    if (resConfig) return [resConfig, resVersion];

    resConfig = await getInstallDirConfigJson(this.pipeExec);
    if (resConfig) return [resConfig, resVersion];

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
        this.resetVersion();
        break;
      }
      case configureActions.cpUrl: {
        resConfig = await configureWithCpUrl(this.cpExtConfig, this.logger);
        this.resetVersion();
        break;
      }

      case configureActions.cpAuth: {
        resConfig = await configureWithOAuth(this.cpExtConfig, this.logger);
        this.resetVersion();
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
    return [resConfig, resVersion];
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
