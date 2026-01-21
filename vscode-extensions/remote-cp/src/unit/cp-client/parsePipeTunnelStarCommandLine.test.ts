/// <reference types="jest" />

import { parsePipeTunnelStartCommandLine } from "../../cp-client/tunnel/parse-pipe-tunnel-start";

describe("parsePipeTunnelStarCommandLine", () => {
  it("correct", () => {
    const cmdline = `"c:\\Users\\aleksandr_tanas\\AppData\\Roaming\\Code - Insiders\\User\\globalStorage\\epam.remote-cp\\bin\\pipe\\pipe-cli.exe" tunnel start -f --ssh --ignore-existing -rp 22 -lp 54116 --log-level INFO 85750`;
    const result = parsePipeTunnelStartCommandLine(cmdline);
    expect(result).toEqual({ localPort: 54116, host: 85750 });
  });

  it("correct2", () => {
    const cmdline = `"c:\\Users\\aleksandr_tanas\\AppData\\Roaming\\Code - Insiders\\User\\globalStorage\\epam.remote-cp\\bin\\pipe\\pipe-cli.exe" tunnel start -f --ssh --ignore-existing -rp 22 -lp 54116 85750 --log-level INFO`;
    const result = parsePipeTunnelStartCommandLine(cmdline);
    expect(result).toEqual({ localPort: 54116, host: 85750 });
  });

  it("invalid", () => {
    const cmdline = "invalid command line";
    expect(() => {
      parsePipeTunnelStartCommandLine(cmdline);
    }).toThrow();
  });
});
