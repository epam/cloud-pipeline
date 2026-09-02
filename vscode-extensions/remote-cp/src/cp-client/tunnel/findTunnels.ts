import psList from "ps-list";
import * as os from "os";
import { ILogger } from "../../common/logger";

export interface TunnelProcess<T> {
  pid: number;
  ppid: number;
  owner: string;
  args: string[];
  parsedArgs: T;
}

/**
 * Searches for processes representing tunnels and parses their arguments.
 * @param parseTunnelArgs - Function to parse arguments; return parsed data or undefined if not a tunnel.
 * @param logger - Logger for informational and debug messages.
 * @returns Array of TunnelProcess<T> for each detected tunnel.
 */
export async function findTunnels<T>(
  parseTunnelArgs: (args: string[]) => T | undefined,
  logger: ILogger,
): Promise<TunnelProcess<T>[]> {
  logger.info("Searching for tunnel processes...");
  const currentPid = process.pid;
  const currentPpid = process.ppid;
  const skipPids = new Set<number>([currentPid, currentPpid]);
  const processes = await psList();
  const tunnels: TunnelProcess<T>[] = [];

  for (const proc of processes) {
    const { pid, ppid, cmd } = proc;
    try {
      if (skipPids.has(pid)) {
        logger.debug(
          `Skipping process #${pid} because it is current process or its parent...`,
        );
        continue;
      }
      if (!cmd) continue;
      const args = cmd.split(/\s+/);
      const parsed = parseTunnelArgs(args);
      if (!parsed) {
        continue;
      }
      logger.info(`Tunnel process #${pid} was found (${cmd})`);
      const owner = os.userInfo().username;
      tunnels.push({ pid, ppid, owner, args, parsedArgs: parsed });
    } catch (error) {
      logger.debug(
        `Skipping process #${pid} because its details retrieval has failed.`,
        error,
      );
    }
  }

  return tunnels;
}
