/**
 * Tunnel stop command implementation
 */

import { ILogger, TunnelStopOptions } from "cp-client-common";
import { TunnelManager } from "cp-client-tunnel";
import { TunnelStopCommandOptions } from "../types";
import { TunnelManagerConfig } from "../options/tunnel-manager";

export async function tunnelStopAction(
  runIdStr: string | undefined,
  cmdOpts: TunnelStopCommandOptions,
  logger: ILogger
): Promise<void> {
  try {
    const runId = runIdStr ? parseInt(runIdStr, 10) : undefined;
    if (runIdStr && isNaN(runId!)) {
      console.error("Invalid run ID (must be numeric)");
      process.exit(1);
    }

    const config = TunnelManagerConfig.fromCommandOptions(cmdOpts, logger);
    const options: TunnelStopOptions = {
      localPort: cmdOpts.localPort ? parseInt(cmdOpts.localPort) : undefined,
      force: cmdOpts.force,
      timeoutStop: cmdOpts.timeoutStop ? parseInt(cmdOpts.timeoutStop) : 60,
      logLevel: cmdOpts.logLevel as any,
      user: cmdOpts.user,
      debug: cmdOpts.debug,
      trace: cmdOpts.trace,
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
    } else if (cmdOpts.localPort) {
      console.log(`Tunnel on port ${cmdOpts.localPort} stopped`);
    }
  } catch (err) {
    console.error("Error stopping tunnel:", err);
    process.exit(1);
  }
}
