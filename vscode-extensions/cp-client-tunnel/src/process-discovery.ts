import psList from "ps-list";
import { ILogger } from "cp-client-common";
import { ITunnelInfo } from "cp-client-common";

/**
 * Parse tunnel arguments from process command line.
 * Looks for tunnel start process and extracts configuration.
 */
interface ParsedTunnelArgs {
  runId?: number;
  localPort?: number;
  remotePort?: number;
}

function parseTunnelArgs(cmdline: string[]): ParsedTunnelArgs | null {
  // Look for tunnel start command
  const tunnelIdx = cmdline.indexOf("tunnel");
  const startIdx = cmdline.indexOf("start", tunnelIdx);

  if (tunnelIdx < 0 || startIdx < 0) {
    return null;
  }

  const args: ParsedTunnelArgs = {};

  // Extract runId (first positional after "start")
  if (startIdx + 1 < cmdline.length) {
    const runIdStr = cmdline[startIdx + 1];
    if (/^\d+$/.test(runIdStr)) {
      args.runId = parseInt(runIdStr, 10);
    }
  }

  // Extract -lp/--local-port
  let lpIdx = cmdline.indexOf("-lp");
  if (lpIdx < 0) {
    lpIdx = cmdline.indexOf("--local-port");
  }
  if (lpIdx >= 0 && lpIdx + 1 < cmdline.length) {
    const portStr = cmdline[lpIdx + 1];
    const port = parseInt(portStr, 10);
    if (!isNaN(port)) {
      args.localPort = port;
    }
  }

  // Extract -rp/--remote-port
  let rpIdx = cmdline.indexOf("-rp");
  if (rpIdx < 0) {
    rpIdx = cmdline.indexOf("--remote-port");
  }
  if (rpIdx >= 0 && rpIdx + 1 < cmdline.length) {
    const portStr = cmdline[rpIdx + 1];
    const port = parseInt(portStr, 10);
    if (!isNaN(port)) {
      args.remotePort = port;
    }
  }

  return args;
}

/**
 * Find all active tunnel processes.
 * Uses process iteration to detect tunnel start commands.
 * Based on Python pipe-cli find_tunnels algorithm.
 */
export async function findExistingTunnels(
  logger?: ILogger,
): Promise<ITunnelInfo[]> {
  logger?.debug("Scanning for existing tunnel processes");

  try {
    const processes = await psList();
    const tunnels: ITunnelInfo[] = [];
    const currentPid = process.pid;

    for (const proc of processes) {
      // Skip self and parent
      if (proc.pid === currentPid) {
        continue;
      }

      // Look for Node processes with tunnel command
      if (
        proc.name &&
        (proc.name.includes("node") ||
          proc.name.includes("pipe") ||
          proc.name.includes("tunnel"))
      ) {
        // Parse command line for tunnel args
        const cmd = proc.cmd || "";
        const cmdlineArray = cmd.split(/\s+/);

        if (cmdlineArray.includes("tunnel") && cmdlineArray.includes("start")) {
          const parsed = parseTunnelArgs(cmdlineArray);

          if (parsed && parsed.runId !== undefined && parsed.localPort) {
            logger?.debug(
              `Found tunnel: pid=${proc.pid}, runId=${parsed.runId}, lp=${parsed.localPort}`,
            );

            tunnels.push({
              pid: proc.pid,
              parentPid: proc.ppid || null,
              owner: proc.uid ? String(proc.uid) : "unknown",
              runId: parsed.runId,
              localPort: parsed.localPort,
              remotePort: parsed.remotePort || 22,
            });
          }
        }
      }
    }

    logger?.info(`Found ${tunnels.length} active tunnel(s)`);
    return tunnels;
  } catch (err) {
    logger?.error(`Error finding tunnels: ${err}`);
    return [];
  }
}
