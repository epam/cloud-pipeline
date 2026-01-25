/**
 * Tunnel start command implementation
 */

import { TunnelStartOptions, ILogger } from "cp-client-common";
import { createTunnelManagerConfig } from "../utils";
import { TunnelStartCommandOptions } from "../types";
import { startTunnelBackground } from "./start-tunnel-background";
import { startTunnelForward } from "./start-tunnel-forward";

export async function tunnelStartAction(
  runIdStr: string,
  cmdOptions: TunnelStartCommandOptions,
  logger: ILogger,
): Promise<void> {
  try {
    const runId = parseInt(runIdStr, 10);
    if (isNaN(runId)) {
      logger.error("Invalid run ID (must be numeric)");
      process.exit(1);
    }
    const config = createTunnelManagerConfig(cmdOptions, logger);

    if (cmdOptions.foreground) {
      // Foreground mode: keep process alive
      const options: TunnelStartOptions = {
        ...cmdOptions,
        localPort: cmdOptions.localPort ? parseInt(cmdOptions.localPort) : undefined,
        remotePort: cmdOptions.remotePort ? parseInt(cmdOptions.remotePort) : 22,
        connectionTimeout: cmdOptions.connectionTimeout ? parseInt(cmdOptions.connectionTimeout) : 0,
        timeout: cmdOptions.timeout ? parseInt(cmdOptions.timeout) : 300,
        timeoutStop: cmdOptions.timeoutStop ? parseInt(cmdOptions.timeoutStop) : 60,
        logLevel: cmdOptions.logLevel as any,
        foreground: cmdOptions.foreground ? true : false,
      };
      await startTunnelForward(runId, options, config, logger);
    } else {
      // Background mode: spawn detached process
      await startTunnelBackground(runId, cmdOptions, config, logger);
    }
  } catch (err) {
    logger.error("Error starting tunnel:", err);
    process.exit(1);
  }
}
