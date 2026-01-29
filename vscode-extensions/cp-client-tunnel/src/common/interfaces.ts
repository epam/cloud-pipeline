import { Duplex } from "stream";
import { IDisposable, ITunnelInfo, ITunnelConfig } from "cp-client-common";

/**
 * Network endpoint (host + port).
 * Maps to pipe-cli target_endpoint and proxy_endpoint tuples.
 * 
 * Example:
 *   targetEndpoint = { host: '10.244.78.133', port: 22 }  (maps to pipe-cli target_endpoint)
 *   proxyEndpoint = { host: 'edge.aws.cloud-pipeline.com', port: 443 }  (maps to pipe-cli proxy_endpoint)
 */
export interface Endpoint {
  host: string;
  port: number;
}

export interface ProxyEndpoint extends Endpoint {
  username?: string;
  password?: string;
}

/**
 * Represents an active tunnel connection.
 */
export interface ITunnelConnection extends IDisposable {
  readonly runId: number;
  readonly localPort?: number;
  readonly remotePort: number;
  readonly pid?: number;
  readonly owner?: string;

  /**
   * Get stream after HTTP CONNECT (pre-SSH) for use by callers managing SSH themselves.
   * Returns a Duplex stream connecting through proxy to remote endpoint.
   */
  getStream(): Promise<Duplex>;
}

/**
 * Main tunnel manager API.
 */
export interface ITunnelManager extends IDisposable {
  /**
   * Create a new tunnel connection to a run.
   */
  createTunnel(config: Partial<ITunnelConfig>): Promise<ITunnelConnection>;

  /**
   * List all active tunnel connections.
   */
  listTunnels(): Promise<ITunnelInfo[]>;

  /**
   * Stop a running tunnel by runId or localPort.
   */
  stopTunnel(runId?: number, localPort?: number): Promise<void>;
}
