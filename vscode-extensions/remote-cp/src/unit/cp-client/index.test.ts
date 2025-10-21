/// <reference types="jest" />

import { ILogger } from "../../common/logger";
import { ICpExtConfig } from "../../config";
import { CpClientBase, CpVersionInfo, RunInfo } from "../../cp-client";

interface IClient {
  parseRunListTable(tableStr: string): RunInfo[];
}

class CpClientTest extends CpClientBase {
  static create(cpExtConfig: ICpExtConfig, logger: ILogger): CpClientTest {
    return new CpClientTest(cpExtConfig, logger);
  }

  public override ensurePipeExec(): Promise<CpVersionInfo> {
    throw new Error("Not implemented");
  }
}

describe("CloudPipelineClient.parseRunListTable", () => {
  const notImplementedStubFunc = () => {
    throw new Error("Not implemented");
  };
  const cpConfig: ICpExtConfig = {
    globalStoragePath: "",
    platformUrl: "https://cora.company.com",
    prefix: "CP:",
    apiEndpoint: "/pipeline/restapi",
    authEndpoint: "/pipeline/restapi/route",
    pipeApiUri: null,
    pipeApiToken: null,
    pipeSnoozeUpdate: null,
    logLevel: "trace",
    onStart: [],

    getClientConfig: notImplementedStubFunc,
    setClientConfig: notImplementedStubFunc,

    save: notImplementedStubFunc,
  };
  const client = CpClientTest.create(cpConfig, console) as any as IClient;

  it("parses valid table with multiple runs", () => {
    const table = `\
+-------+--------------+--------------+---------+--------+------------------+-------+        
| RunID | Parent RunID | Pipeline     | Version | Status | Started          | Owner |
+-------+--------------+--------------+---------+--------+------------------+-------+        
| 124   | 123          | pipe1:latest | v1.0    | SUCCESS| 2024-06-01 11:00 | user2 |
| 123   | None         | pipe1:latest | v1.0    | RUNNING| 2024-06-01 10:00 | user1 |
| 128   | None         | pipe2:4.0.0  | None    | SUCCESS| 2024-06-01 12:00 | user2 |
+-------+--------------+--------------+---------+--------+------------------+-------+        
`;
    const result = client.parseRunListTable(table);
    expect(result).toEqual([
      {
        runId: 124,
        parentRunId: 123,
        pipeline: "pipe1:latest",
        version: "v1.0",
        status: "SUCCESS",
        started: "2024-06-01 11:00",
        owner: "user2",
      },
      {
        runId: 123,
        parentRunId: null,
        pipeline: "pipe1:latest",
        version: "v1.0",
        status: "RUNNING",
        started: "2024-06-01 10:00",
        owner: "user1",
      },

      {
        runId: 128,
        parentRunId: null,
        pipeline: "pipe2:4.0.0",
        version: null,
        status: "SUCCESS",
        started: "2024-06-01 12:00",
        owner: "user2",
      },
    ]);
  });

  it("returns empty array for empty table", () => {
    const table = "";
    const result = client.parseRunListTable(table);
    expect(result).toEqual([]);
  });

  it("returns empty array for table with only header", () => {
    const table = `
| RunID | Parent RunID | Pipeline | Version | Status | Started | Owner |
`;
    const result = client.parseRunListTable(table);
    expect(result).toEqual([]);
  });

  it("handles malformed table (missing columns)", () => {
    const table = `
+-------+--------------+--------------+---------+--------+------------------+-------+        
| RunID | Parent RunID | Pipeline | Version | Status | Started | Owner |
+-------+--------------+--------------+---------+--------+------------------+-------+        
| 125   | 101          | pipe3    | v2.0    | FAILED | 2024-06-01 12:00 |
+-------+--------------+--------------+---------+--------+------------------+-------+        
`;
    const result = client.parseRunListTable(table);
    // Should still parse, but missing owner will be undefined
    expect(result).toEqual([
      {
        runId: 125,
        parentRunId: 101,
        pipeline: "pipe3",
        version: "v2.0",
        status: "FAILED",
        started: "2024-06-01 12:00",
        owner: undefined,
      },
    ]);
  });

  it("ignores separator lines and trims whitespace", () => {
    const table = `
+-------+--------------+--------------+---------+--------+------------------+-------+        
| RunID | Parent RunID | Pipeline | Version | Status | Started | Owner |
+-------+--------------+--------------+---------+--------+------------------+-------+        
| 126  | None         | pipe4    | v3.0    | QUEUED | 2024-06-01 13:00 | user3 |
`;
    const result = client.parseRunListTable(table);
    expect(result).toEqual([
      {
        runId: 126,
        parentRunId: null,
        pipeline: "pipe4",
        version: "v3.0",
        status: "QUEUED",
        started: "2024-06-01 13:00",
        owner: "user3",
      },
    ]);
  });
});
