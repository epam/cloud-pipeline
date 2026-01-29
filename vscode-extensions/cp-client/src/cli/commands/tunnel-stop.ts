/**
 * Tunnel stop command implementation
 */

import { ILogger, TunnelStopOptions } from "cp-client-common";
import { TunnelManager } from "cp-client-tunnel";
import { createTunnelManagerConfig } from "../utils";
import { TunnelStopCommandOptions } from "../types";

export async function tunnelStopAction(
  runIdStr: string | undefined,
  opts: TunnelStopCommandOptions,
  logger: ILogger
): Promise<void> {
  try {
    const runId = runIdStr ? parseInt(runIdStr, 10) : undefined;
    if (runIdStr && isNaN(runId!)) {
      console.error("Invalid run ID (must be numeric)");
      process.exit(1);
    }

    const config = createTunnelManagerConfig(opts, logger);
    const options: TunnelStopOptions = {
      localPort: opts.localPort ? parseInt(opts.localPort) : undefined,
      force: opts.force,
      timeoutStop: opts.timeoutStop ? parseInt(opts.timeoutStop) : 60,
      logLevel: opts.logLevel as any,
      user: opts.user,
      debug: opts.debug,
      trace: opts.trace,
    };

    const manager = new TunnelManager(config, logger);
    try {
      const localPort = options.localPort
        ? parseInt(String(options.localPort))
        : undefined;
      await manager.stopTunnel(runId, localPort);
    } finally {
      manager.dispose();
    }

    if (runId) {
      console.log(`Tunnel for run ${runId} stopped`);
    } else if (opts.localPort) {
      console.log(`Tunnel on port ${opts.localPort} stopped`);
    }
  } catch (err) {
    console.error("Error stopping tunnel:", err);
    process.exit(1);
  }
}
