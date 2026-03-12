import * as vscode from "vscode";
import * as cp from "node:child_process";

import { ILogger } from "../common/logger";
import { pipeParse } from "./pipeParse";
import { PipeTunnel } from "../pipeTunnel";
import { Disposable } from "../common/disposable";
import { CpClientMode, ICpExtConfig } from "../config";
import { fileExists, readJsonFile } from "../common/files/file";
import { ICpClientConfig } from "./cp-client-config";
import {
  CpAuthInvalidError,
  CpTokenExpiredError,
  UserCancelledError,
} from "../cp-client/error";
import { configureWithCliConfigurationCommand } from "./configure-with-cli-configuration-command";
import { configureWithCpUrl } from "./configure-with-cp-web-auth";
import { configureWithOAuth } from "./configure-with-oauth";
import { PipeTunnelBase } from "./tunnel/pipe-tunnel-base";
import {
  askUserForPipeTunnel,
  ExecutePipeTunnelItem,
  ReusePipeTunnelItem,
  EnterLocalPortItem,
  CreateTunnelOnLocalPortItem,
  CreateTunnelInternalItem,
} from "./tunnel/ask-user-for-pipe-tunnel";
import { findRandomPort } from "../common/ports";
import { ReusedPipeTunnel } from "./tunnel/reusing-pipe-tunnel";
import { NodeJSTunnelClient } from "./tunnel/nodejs-tunnel-client";
import { ICpCodeContext } from "../cp-ext/code-context";
import {
  ActionQuickPickItem,
  quickPickWithCountdown,
} from "../common/quick-pick-with-countdown";
import { parsePipeTunnelStartCommandLine } from "./tunnel/parse-pipe-tunnel-start";
import { IRunInfo, RunAPI } from "cp-client-api";

function pipelineRunToRunInfo(r: IRunInfo): RunInfo {
  return new RunInfo(
    r.id,
    r.parentId,
    r.pipelineName,
    r.version,
    r.status,
    r.startDate,
    r.owner,
  );
}

export enum PipeRunCols {
  id = "RunID",
  parentId = "Parent RunID",
  pipelineName = "Pipeline",
  version = "Version",
  status = "Status",
  startDate = "Started",
  owner = "Owner",
}

export class RunInfo implements IRunInfo {
  public locations?: RunLocation[];

  constructor(
    public id: number,
    public parentId: number | null,
    public pipelineName: string,
    public version: string | null,
    public status: string,
    public startDate: string,
    public owner: string,
  ) { }
}

export class RunLocation {
  constructor(
    public run: RunInfo,
    public path: string,
  ) { }
}

export function pipeParseRunList(table: string): RunInfo[] {
  return pipeParse<RunInfo>(table, (cells, _header: string[]) => {
    const res = new RunInfo(
      /* id: */ Number.parseInt(cells[0]),
      /* parentId: */ cells[1] === "None" ? null : Number.parseInt(cells[1]),
      /* pipelineName: */ cells[2],
      /* version: */ cells[3] === "None" ? null : cells[3],
      /* status: */ cells[4],
      /* startDate: */ cells[5],
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
  ) { }
}

export interface ExecPipeTunnelInfo {
  pid: number;
  host: number;
  localPort: number;
}

export function pipeParseTunnelList(table: string): PipeTunnelInfo[] {
  return pipeParse<PipeTunnelInfo>(table, (cells, _header: string[]) => {
    const res = new PipeTunnelInfo(
      /* PID: */ Number.parseInt(cells[0]),
      /* PPID: */ cells[1] === "None" ? null : Number.parseInt(cells[1]),
      /* Owner: */ cells[2],
      /* Host: */ Number.parseInt(cells[3]),
      /* LocalPorts: */ Number.parseInt(cells[4]),
      /* RemotePorts: */ Number.parseInt(cells[5]),
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
  ) { }
}

/**
 * pipe-cli
 *   Tries to use {install-dir}/config.json
 *   and fallbacks to {home-dir}/config.json auto and silent
 */
export abstract class CpClientBase extends Disposable {
  private static objCounter = 0;
  private readonly objId = CpClientBase.objCounter++;

  protected toLog(): string {
    return `${this.constructor.name}<${this.objId}>`;
  }

  protected pipeExec!: string;

  protected constructor(
    public readonly cpExtConfig: ICpExtConfig,
    public readonly codeContext: ICpCodeContext,
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

  // TODO: Reset `this.version` on change
  private version: CpVersionInfo | null = null;

  async getVersion(): Promise<CpVersionInfo> {
    if (this.version) return this.version;

    const output = await this.execPipeCommand("--version");
    const apiM = /^Cloud Pipeline API, version (\d+\.\d+\.\d+\.\d+)\.([0-9a-f]{40})/m.exec(
      output,
    );
    const cliM = /^Cloud Pipeline CLI, version (\d+\.\d+\.\d+\.\d+)\.([0-9a-f]{40})/m.exec(
      output,
    );
    const tokenM =
      /^Access token info:\s*\nIssued to: (\w+)\s*\nIssued at: (\d{4}-\d{2}-\d{2} \d{2}:\d{2})\s*\nExpires at: (\d{4}-\d{2}-\d{2} \d{2}:\d{2})/m.exec(
        output,
      );
    if (!apiM && cliM && tokenM) {
      throw new CpTokenExpiredError(`Access token is expired: ${tokenM[3]}`);
    } else if (!apiM || !cliM || !tokenM) {
      throw new Error(`Failed to parse 'pipe --version' output: ${output}`);
    }

    // prettier-ignore
    this.version = new CpVersionInfo(
      apiM[1], apiM[2],
      cliM[1], cliM[2],
      tokenM[1], tokenM[2], tokenM[3],
    );
    return this.version;
  }

  protected resetVersion(): undefined {
    this.version = null;
  }

  /**
   * Gets run list: from CLI (`pipe view-runs`) when cpClientMode is cli, otherwise from API.
   */
  async getRunList(): Promise<RunInfo[]> {
    const logPfx = `${this.toLog()}.getRunList()`;
    this.logger.info(`${logPfx}, start`);
    try {
      if (this.cpExtConfig.cpClientMode === CpClientMode.cli) {
        const output = await this.execPipeCommand(`view-runs`);
        const res = pipeParseRunList(output);
        this.logger.info(`${logPfx}, end`);
        return res;
      } else {
        const config = await this.cpExtConfig.getClientConfig();
        if (!config)
          throw new Error("API config is not set (TODO)");
        const runApi = new RunAPI(
          { url: config.apiUri, token: config.apiToken },
          this.logger,
        );
        const result = await runApi.listRuns();
        const res = result.elements.map(pipelineRunToRunInfo);
        this.logger.info(`${logPfx}, end (API, ${res.length} runs)`);
        return res;
      }
    } finally {
      await this.cpExtConfig.save(logPfx);
      this.logger.info(`${logPfx}, finally`);
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
      const _m = CpTokenExpiredError.re.exec(err.message);
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
        const fltStderrList = [];
        for (const stderrLine of stderr.split("\n")) {
          const m = CpTokenExpiredError.soonRe.exec(stderrLine);
          if (m) {
            vscode.window.showInformationMessage(
              `${this.cpExtConfig.prefix} pipe client: ${stderrLine}`,
            );
          } else {
            fltStderrList.push(stderrLine);
          }
        }
        const fltStderr = fltStderrList.join("\n");
        if (error || fltStderr) {
          const m =
            CpTokenExpiredError.re.exec(error?.message ?? "") ??
            CpTokenExpiredError.re.exec(fltStderr);
          if (m) {
            reject(new CpTokenExpiredError(m[0]));
          } else {
            reject(error ?? new Error(fltStderr));
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

  protected async getExecTunnelList(): Promise<ExecPipeTunnelInfo[]> {
    if (process.platform !== "win32") return [];

    const normalizedPath = this.pipeExec;
    const psScript =
      `Get-CimInstance Win32_Process | ` +
      `Where-Object { $_.ExecutablePath -eq "${normalizedPath}" } | ` +
      `Select-Object ProcessId,CommandLine |` +
      ` ConvertTo-Json -Compress`;

    const stdout = await new Promise<string>((resolve, reject) => {
      const child = cp.spawn("powershell.exe", [
        "-NoProfile",
        "-Command",
        psScript,
      ]);

      let outBuf = "";
      child.stdout.on("data", (data) => {
        outBuf += data.toString();
      });
      child.on("error", (err) => {
        reject(new Error(`Failed to list tunnel processes: ${err.message}`));
      });
      child.on("close", (code) => {
        if (code === 0) {
          resolve(outBuf);
        } else {
          const errMsg = `Failed to list tunnel processes: exit code ${code}`;
          reject(new Error(errMsg));
        }
      });
    });

    if (!stdout) return [];
    const stdoutObj = JSON.parse(stdout);
    const list: {
      ProcessId: number;
      CommandLine: string;
    }[] = Array.isArray(stdoutObj) ? stdoutObj : [stdoutObj];
    const res: ExecPipeTunnelInfo[] = [];

    for (const item of list) {
      const pid = Number(item?.ProcessId);
      const cmdline = String(item?.CommandLine ?? "");
      const parsedCmd = parsePipeTunnelStartCommandLine(cmdline);
      if (!pid || !parsedCmd) continue;
      res.push({ pid, host: parsedCmd.host, localPort: parsedCmd.localPort });
    }

    return res;
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

      const pipeTunnelUserResp = await askUserForPipeTunnel(
        cpRunId,
        runTunnelList,
      );

      if (pipeTunnelUserResp instanceof ReusePipeTunnelItem) {
        resPipeTunnel = new ReusedPipeTunnel(pipeTunnelUserResp.tunnelInfo);
      } else if (pipeTunnelUserResp instanceof ExecutePipeTunnelItem) {
        this.logger.info(`${logPfx}, user resp to execute pipe tunnel`);
        resPipeTunnel = await (async (): Promise<PipeTunnelBase> => {
          const logPfx2 = `${logPfx}.execPipeTunnel`;
          this.logger.info(`${logPfx2}, start`);
          const localPort = await findRandomPort();
          this.logger.info(`${logPfx2}, localPort: ${localPort}`);
          const res = new PipeTunnel(
            cpRunId,
            localPort,
            pipeTunnelUserResp.toStop,
            this.cpExtConfig,
            this.logger,
          );
          this.logger.info(`${logPfx2}, created`);
          // context.subscriptions.push(resPipeTunnel);
          await res.activate(
            async (
              localPort: number,
              toStop: boolean,
            ): Promise<[cp.ChildProcessWithoutNullStreams, CpVersionInfo]> => {
              const logPfx3 = `${logPfx2}.startProcess`;
              this.logger.info(`${logPfx3}, start`);
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
              this.logger.info(`${logPfx3}, end (spawned)`);
              return [resProcess, resVersion];
            },
          );
          this.logger.info(`${logPfx2}, end (activated)`);
          return res;
        })();
      } else if (pipeTunnelUserResp instanceof CreateTunnelOnLocalPortItem) {
        this.logger.info(`${logPfx}, user resp to create tunnel on local port`);
        resPipeTunnel = await (async (): Promise<PipeTunnelBase> => {
          const logPfx2 = `${logPfx}.createOnLocalPort`;
          this.logger.info(`${logPfx2}, start`);
          const localPort = await findRandomPort();
          this.logger.info(`${logPfx2}, localPort: ${localPort}`);
          const res = new NodeJSTunnelClient(
            cpRunId,
            localPort,
            true,
            this.cpExtConfig,
            this.logger,
          );
          this.logger.info(`${logPfx2}, created`);
          await res.activate();
          this.logger.info(`${logPfx2}, end (activated)`);
          return res;
        })();
      } else if (pipeTunnelUserResp instanceof CreateTunnelInternalItem) {
        this.logger.info(`${logPfx}, user resp to create tunnel (internal)`);
        resPipeTunnel = await (async (): Promise<PipeTunnelBase> => {
          const logPfx2 = `${logPfx}.createInternal`;
          this.logger.info(`${logPfx2}, start`);
          const res = new NodeJSTunnelClient(
            cpRunId,
            -1, // -1 indicates internal mode (no local port)
            true,
            this.cpExtConfig,
            this.logger,
          );
          this.logger.info(`${logPfx2}, created`);
          await res.activate();
          this.logger.info(`${logPfx2}, end (activated)`);
          return res;
        })();
      } else if (pipeTunnelUserResp instanceof EnterLocalPortItem) {
        this.logger.info(`${logPfx}, user resp to enter local port`);
        const portStr = await vscode.window.showInputBox({
          title: "Enter local port number",
          validateInput: (value) => {
            const port = Number.parseInt(value);
            if (Number.isNaN(port) || port < 1 || port > 65535) {
              return "Enter a valid port number (1-65535)";
            }
            return "";
          },
        });
        if (!portStr) {
          throw new Error("User cancelled port entry");
        }
        const localPort = Number.parseInt(portStr);
        resPipeTunnel = new ReusedPipeTunnel(
          new PipeTunnelInfo(
            -1,         // pid: manual entry, no process
            null,       // parentPid
            "manual",   // owner
            cpRunId,    // runId
            localPort,  // localPort
            22,         // remotePort
          ),
        );
      }
    }
    this._register(resPipeTunnel!);
    return resPipeTunnel!;
  }

  async stopTunnel(tunnel: PipeTunnel): Promise<void> {
    await tunnel.deactivate();
  }

  private ensurePipeExecActive: boolean = false;

  public abstract ensurePipeExecDo(
    forceUpdate?: boolean,
  ): Promise<CpVersionInfo>;

  public async ensurePipeExec(forceUpdate?: boolean): Promise<CpVersionInfo> {
    try {
      this.codeContext.isUpdatingPipeClient = true;
      return await this.ensurePipeExecDo(forceUpdate);
    } finally {
      this.codeContext.isUpdatingPipeClient = false;
    }
  }

  private async ensurePipeExecInternal(): Promise<CpVersionInfo | null> {
    if (this.ensurePipeExecActive)
      return null;

    this.ensurePipeExecActive = true;
    this.codeContext.isUpdatingPipeClient = true;
    try {
      return await this.ensurePipeExec();
    } finally {
      this.codeContext.isUpdatingPipeClient = false;
      this.ensurePipeExecActive = false;
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

    const configUserResp: string = (
      await quickPickWithCountdown(
        `${this.cpExtConfig.prefix} pipe client is not configured.`,
        [
          { label: "Cancel", action: () => "Cancel" },
          {
            label: "-",
            kind: vscode.QuickPickItemKind.Separator,
            action: () => {
              throw new Error("Unexpected");
            },
          },
          {
            label: configureActions.cpUrl,
            action: () => configureActions.cpUrl,
          },
          {
            label: configureActions.cliConfigurationCommands,
            action: () => configureActions.cliConfigurationCommands,
          },
        ] as ActionQuickPickItem<string>[],
        30000,
      ).result
    ).action();

    if (configUserResp === "Cancel") {
      throw new UserCancelledError("Auth cancelled by user");
    }

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

  protected configToEnv(config: ICpClientConfig): any {
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
