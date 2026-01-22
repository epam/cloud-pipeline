/**
 * Tunnel start command implementation
 */

import { TunnelStartOptions } from "cp-client-common";
import { pipeTunnelStart } from "../../index";
import {
  createTunnelManagerConfig,
  startBackgroundTunnel,
  waitForBackgroundTunnel,
} from "../utils";
import { TunnelStartCommandOptions } from "../types";
import * as path from "path";
import * as os from "os";

export async function tunnelStartAction(
  runIdStr: string,
  opts: TunnelStartCommandOptions,
): Promise<void> {
  try {
    const runId = parseInt(runIdStr, 10);
    if (isNaN(runId)) {
      console.error("Invalid run ID (must be numeric)");
      process.exit(1);
    }

    const config = createTunnelManagerConfig(opts);
    const options: TunnelStartOptions = {
      localPort: opts.localPort,
      remotePort: opts.remotePort || "22",
      connectionTimeout: opts.connectionTimeout
        ? parseInt(opts.connectionTimeout)
        : undefined,
      ssh: opts.ssh,
      sshPath: opts.sshPath,
      sshHost: opts.sshHost,
      sshUser: opts.sshUser,
      sshKeep: opts.sshKeep,
      direct: opts.direct,
      foreground: opts.foreground,
      keepExisting: opts.keepExisting,
      keepSame: opts.keepSame,
      replaceExisting: opts.replaceExisting,
      replaceDifferent: opts.replaceDifferent,
      ignoreExisting: opts.ignoreExisting,
      ignoreOwner: opts.ignoreOwner,
      region: opts.region,
      logFile: opts.logFile,
      timeout: opts.timeout ? parseInt(opts.timeout) : undefined,
      timeoutStop: opts.timeoutStop ? parseInt(opts.timeoutStop) : undefined,
      logLevel: opts.logLevel as any,
      user: opts.user,
      noclean: opts.noclean,
      debug: opts.debug,
      trace: opts.trace,
    };

    if (opts.foreground) {
      // Foreground mode: keep process alive
      const tunnel = await pipeTunnelStart(runId, options, config);

      console.log(`Tunnel started to run ${runId} (foreground mode)`);
      console.log(
        `Local port: ${tunnel.localPort}, Remote port: ${tunnel.remotePort}`,
      );

      // Keep process alive until interrupted
      await new Promise(() => {});
    } else {
      // Background mode: spawn detached process
      const logFile =
        opts.logFile ||
        path.join(os.homedir(), ".pipe", "logs", `tunnel-${runId}.log`);
      const healthCheckTimeout = opts.timeout ? parseInt(opts.timeout) : 300; // 5 minutes default

      console.log(`Launching background tunnel to run ${runId}...`);

      const childPid = await startBackgroundTunnel(logFile, healthCheckTimeout);

      // Determine local port for health check
      const localPort = opts.localPort
        ? parseInt(opts.localPort.split("-")[0])
        : parseInt(opts.remotePort || "22");

      await waitForBackgroundTunnel(childPid, localPort, healthCheckTimeout);

      console.log(`Tunnel started to run ${runId}`);
      console.log(
        `Local port: ${localPort}, Remote port: ${opts.remotePort || 22}`,
      );
      console.log(`Background process PID: ${childPid}`);
      console.log(`Logs: ${logFile}`);
    }
  } catch (err) {
    console.error("Error starting tunnel:", err);
    process.exit(1);
  }
}
