/**
 * Tunnel list command implementation
 */

import { ILogger, TunnelListOptions } from "cp-client-common";
import { TunnelManager } from "cp-client-tunnel";
import { createTunnelManagerConfig } from "../utils";
import { GlobalOptions } from "../types";

export async function tunnelListAction(
  opts: GlobalOptions, logger: ILogger
): Promise<void> {
  try {
    const config = createTunnelManagerConfig(opts, logger);
    const options: TunnelListOptions = {
      logLevel: opts.logLevel as any,
      user: opts.user,
      debug: opts.debug,
      trace: opts.trace,
    };

    const manager = new TunnelManager(config, logger);
    let tunnels;
    try {
      tunnels = await manager.listTunnels();
    } finally {
      manager.dispose();
    }

    if (tunnels.length === 0) {
      console.log("No active tunnels.");
      return;
    }

    console.log("\nActive Tunnels:");
    console.log("-".repeat(80));
    console.log("PID      PPID     Owner         Host     LocalPort  RemotePort");
    console.log("-".repeat(80));

    for (const tunnel of tunnels) {
      console.log(
        `${tunnel.pid.toString().padEnd(8)} ${(tunnel.parentPid || "-").toString().padEnd(8)} ${tunnel.owner.padEnd(13)} ${tunnel.runId.toString().padEnd(8)} ${tunnel.localPort.toString().padEnd(10)} ${tunnel.remotePort}`,
      );
    }
    console.log("-".repeat(80));
  } catch (err) {
    console.error("Error listing tunnels:", err);
    process.exit(1);
  }
}
