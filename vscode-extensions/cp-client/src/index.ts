/**
 * Library API exports for tunnel operations.
 * Can be used programmatically from other projects.
 */

import { TunnelManager, type TunnelManagerConfig } from "cp-client-tunnel";
import {
  TunnelStartOptions,
  TunnelStopOptions,
  TunnelListOptions,
} from "cp-client-common";

/**
 * Start a tunnel to a Cloud Pipeline run.
 * @param runId Run ID to connect to
 * @param options Tunnel start options
 * @param config Tunnel manager configuration
 */
export async function pipeTunnelStart(
  runId: number,
  options: TunnelStartOptions,
  config: TunnelManagerConfig,
) {
  const manager = new TunnelManager(config);
  try {
    const tunnel = await manager.startTunnel(runId, {
      runId,
      localPort: options.localPort ? parseInt(options.localPort) : undefined,
      remotePort: options.remotePort ? parseInt(options.remotePort) : 22,
      region: options.region,
      direct: options.direct,
      ssh: options.ssh,
    });
    return tunnel;
  } finally {
    // Don't dispose manager yet; tunnel is still active
  }
}

/**
 * List all active tunnels.
 * @param options List options
 * @param config Tunnel manager configuration
 */
export async function pipeTunnelList(
  options: TunnelListOptions,
  config: TunnelManagerConfig,
) {
  const manager = new TunnelManager(config);
  try {
    const tunnels = await manager.listTunnels();
    return tunnels;
  } finally {
    manager.dispose();
  }
}

/**
 * Stop a tunnel.
 * @param runId Run ID to stop (optional)
 * @param options Stop options
 * @param config Tunnel manager configuration
 */
export async function pipeTunnelStop(
  runId: number | undefined,
  options: TunnelStopOptions,
  config: TunnelManagerConfig,
) {
  const manager = new TunnelManager(config);
  try {
    const localPort = options.localPort
      ? parseInt(String(options.localPort))
      : undefined;
    await manager.stopTunnel(runId, localPort);
  } finally {
    manager.dispose();
  }
}

export { TunnelManager };
export type { TunnelManagerConfig };
