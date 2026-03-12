/// <reference types="jest" />

import { pipeParseRunList, RunInfo } from "../../cp-client";

describe("pipeParseRunList", () => {
  const parseRunListTable = (tableStr: string): RunInfo[] =>
    pipeParseRunList(tableStr);

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
    const result = parseRunListTable(table);
    expect(result).toEqual([
      {
        id: 124,
        parentId: 123,
        pipelineName: "pipe1:latest",
        version: "v1.0",
        status: "SUCCESS",
        startDate: "2024-06-01 11:00",
        owner: "user2",
      },
      {
        id: 123,
        parentId: null,
        pipelineName: "pipe1:latest",
        version: "v1.0",
        status: "RUNNING",
        startDate: "2024-06-01 10:00",
        owner: "user1",
      },

      {
        id: 128,
        parentId: null,
        pipelineName: "pipe2:4.0.0",
        version: null,
        status: "SUCCESS",
        startDate: "2024-06-01 12:00",
        owner: "user2",
      },
    ]);
  });

  it("returns empty array for empty table", () => {
    const table = "";
    const result = parseRunListTable(table);
    expect(result).toEqual([]);
  });

  it("returns empty array for table with only header", () => {
    const table = `
| RunID | Parent RunID | Pipeline | Version | Status | Started | Owner |
`;
    const result = parseRunListTable(table);
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
    const result = parseRunListTable(table);
    // Should still parse, but missing owner will be undefined
    expect(result).toEqual([
      {
        id: 125,
        parentId: 101,
        pipelineName: "pipe3",
        version: "v2.0",
        status: "FAILED",
        startDate: "2024-06-01 12:00",
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
    const result = parseRunListTable(table);
    expect(result).toEqual([
      {
        id: 126,
        parentId: null,
        pipelineName: "pipe4",
        version: "v3.0",
        status: "QUEUED",
        startDate: "2024-06-01 13:00",
        owner: "user3",
      },
    ]);
  });
});
