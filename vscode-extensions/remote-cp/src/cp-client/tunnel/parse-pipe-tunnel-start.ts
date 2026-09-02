import stringArgv from "string-argv";
import { Command } from "commander";

export function parsePipeTunnelStartCommandLine(cmdLine: string): {
  localPort: number;
  host: number;
} {
  if (process.platform !== "win32") {
    throw new Error("Only win32 is supported.");
  }

  const optionMap: { [optionName: string]: string } = {
    "-lp": "--local-port",
    "-rp": "--remote-port",
  };

  const argv = stringArgv(cmdLine)
    .map((v) => optionMap[v] || v)
    .slice(1); // skip exe path

  let res: { localPort: number; host: number } | null = null;

  const program = new Command();
  program.exitOverride();
  program.allowUnknownOption(false);
  const tunnel = program.command("tunnel");
  tunnel
    .command("start <hostId>")
    .option("--local-port <port>")
    .option("--remote-port <port>")
    .option("-s, --ssh")
    .option("-f, --foreground")
    .option("--ignore-existing")
    .option("-v, --log-level <level>")
    .action((hostId, options) => {
      res = {
        localPort: parseInt(options.localPort),
        host: parseInt(hostId),
      };
    });
  program.parse(argv, { from: "user" });

  if (!res)
    throw new Error(
      `Failed to parse pipe tunnel start command line: ${cmdLine}.`,
    );

  return res;
}
