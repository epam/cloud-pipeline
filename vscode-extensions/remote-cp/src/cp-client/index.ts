import { exec } from "child_process";
import * as vscode from "vscode";

import { ILogger } from "../common/logger";
import { pipeParse } from "./pipeParse";
import { PipeTunnel } from "../pipeTunnel";
import { Disposable } from "../common/disposable";
import { ICpConfig } from "../config";

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

export class CloudPipelineClient extends Disposable {
  private readonly pipeExec: string;
  constructor(
    pipeExec: string | null,
    public readonly cpConfig: ICpConfig,
    private logger: ILogger,
  ) {
    super();
    this.pipeExec = pipeExec
      ? pipeExec.includes(" ")
        ? `"${pipeExec}"`
        : pipeExec
      : "pipe";
  }

  override dispose() {
    if (!this.isDisposed) {
      // FIX: Cleanup resources
    }
    super.dispose();
  }

  async getVersion(): Promise<CpVersionInfo> {
    const output = await this.execPipeCommand(`${this.pipeExec} --version`);
    const apiM = output.match(
      /^Cloud Pipeline API, version (\d+\.\d+\.\d+\.\d+)\.([0-9a-f]{40})/m,
    );
    const cliM = output.match(
      /^Cloud Pipeline CLI, version (\d+\.\d+\.\d+\.\d+)\.([0-9a-f]{40})/m,
    );
    const tokenM = output.match(
      /^Access token info:\s*\nIssued to: (\w+)\s*\nIssued at: (\d{4}-\d{2}-\d{2} \d{2}:\d{2})\s*\nExpires at: (\d{4}-\d{2}-\d{2} \d{2}:\d{2})/m,
    );
    if (!apiM || !cliM || !tokenM) {
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
    const output = await this.execPipeCommand(`${this.pipeExec} view-runs`);
    return pipeParseRunList(output);
  }

  private execPipeCommand(command: string): Promise<string> {
    return new Promise((resolve, reject) => {
      exec(command, { encoding: "utf8" }, (error, stdout, stderr) => {
        if (error) {
          reject(stderr || error.message);
        } else {
          resolve(stdout);
        }
      });
    });
  }

  async getTunnelList(): Promise<TunnelInfo[]> {
    const output = await this.execPipeCommand(`${this.pipeExec} tunnel list`);
    return pipeParseTunnelList(output);
  }

  async startTunnel(
    cpRunId: number,
    context: vscode.ExtensionContext,
  ): Promise<PipeTunnel> {
    const resPipeTunnel = new PipeTunnel(cpRunId, this.cpConfig, this.logger);
    this._register(resPipeTunnel);
    // context.subscriptions.push(resPipeTunnel);
    await resPipeTunnel.activate();

    return resPipeTunnel;
  }

  async stopTunnel(tunnel: PipeTunnel): Promise<void> {
    await tunnel.deactivate();
  }
}
