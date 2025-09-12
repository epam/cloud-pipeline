import { exec } from "child_process";
import { ILogger } from "../common/logger";

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

export class CloudPipelineClient {
  constructor(private logger: ILogger) {}

  /**
   * Gets run list with `pipe view-runs` command
   */
  async getRunList(): Promise<RunInfo[]> {
    const output = await this.execPipeCommand("pipe view-runs");
    return this.parseRunListTable(output);
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

  private parseRunListTable(table: string): RunInfo[] {
    const lines = table
      .split("\n")
      .filter(
        (line) => line.trim().startsWith("|") /* remove separator lines */,
      );
    if (lines.length < 2) {
      return [];
    }

    // First line is header
    const headerLine = lines[0];
    const headerColNameList = headerLine
      .split("|")
      .map((h) => h.trim())
      .slice(1, -1);

    const runLineList = lines.slice(1); // skip header

    return runLineList
      .map((line) =>
        line
          .split("|")
          .map((cell) => cell.trim())
          .slice(1, -1),
      )
      .map((cells) => {
        const resRunInfo = new RunInfo(
          /* runId: */ parseInt(cells[0]),
          /* parentRunId: */ cells[1] === "None" ? null : parseInt(cells[1]),
          /* pipeline: */ cells[2],
          /* version: */ cells[3] === "None" ? null : cells[3],
          /* status: */ cells[4],
          /* started: */ cells[5],
          /* owner: */ cells[6],
        );
        return resRunInfo;
      });
  }
}
