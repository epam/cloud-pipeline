import { TunnelStartOptions, ILogger } from "cp-client-common";
import { TunnelManager, TunnelManagerConfig } from "cp-client-tunnel";

/**
 * Start tunnel in foreground mode and keep process alive
 */
export async function startTunnelForeground(
  options: TunnelStartOptions,
  config: TunnelManagerConfig,
  logger: ILogger
): Promise<void> {
  logger.trace(`startTunnelForeground() called:\n  runId=${options.runId}, options=${JSON.stringify(options)}`);
  const manager = new TunnelManager(config, logger);
  logger.info(`TunnelManager created, calling createTunnel...`);
  const tunnel = await manager.createTunnel({
    runId: options.runId,
    localPort: options.localPort,
    remotePort: options.remotePort,
    region: options.region,
    direct: options.direct,
    ssh: options.ssh,
  });
  logger.info(`Tunnel created: localPort=${tunnel.localPort}, remotePort=${tunnel.remotePort}`);

  logger.info(`Tunnel started to run ${options.runId} (foreground mode)`);
  logger.info(
    `Local port: ${tunnel.localPort}, Remote port: ${tunnel.remotePort}`
  );

  // Keep process alive until interrupted (Ctrl+C) and shutdown gracefully
  logger.info("Press Ctrl+C to stop the tunnel and exit.");

  const waitForSignal = () => new Promise<string>((resolve) => {
    // Resume stdin to keep event loop alive and listen for signals
    process.stdin.resume();

    const cleanup = () => {
      process.removeListener("SIGINT", onSigInt);
      process.removeListener("SIGTERM", onSigTerm);
      process.removeListener("SIGBREAK", onSigBreak);
      // Pause stdin to allow process to exit
      if (!process.stdin.readableEnded) {
        process.stdin.pause();
      }
    };
    const onSigInt = () => {
      logger.info("SIGINT received (Ctrl+C). Stopping tunnel...");
      cleanup();
      resolve("SIGINT");
    };
    const onSigTerm = () => {
      logger.info("SIGTERM received. Stopping tunnel...");
      cleanup();
      resolve("SIGTERM");
    };
    const onSigBreak = () => {
      logger.info("SIGBREAK received. Stopping tunnel...");
      cleanup();
      resolve("SIGBREAK");
    };
    process.once("SIGINT", onSigInt);
    process.once("SIGTERM", onSigTerm);
    // Windows Ctrl+Break
    process.once("SIGBREAK", onSigBreak as NodeJS.SignalsListener);
  });

  await waitForSignal();

  try {
    await manager.stopTunnel(options.runId, tunnel.localPort);
  } catch (e) {
    logger.warn("Error while stopping tunnel:", e);
  } finally {
    manager.dispose();
  }

  logger.info("Tunnel stopped. Exiting.");
}
