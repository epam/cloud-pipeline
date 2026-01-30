import {
  ITunnelConnection,
  ITunnelManager,
  Endpoint,
  ProxyEndpoint,
  ITunnelManagerConfig
} from "./types";
import { Disposable, ILogger, ITunnelConfig, ITunnelInfo } from "cp-client-common";
import { TunnelConnection } from "./tunnel-connection";
import { httpProxyTunnelConnect } from "./proxy";
import { findExistingTunnels } from "./process-discovery";
import { getRunConnectionInfo } from "./connection-info";

/**
 * Main tunnel manager implementation.
 * Provides createTunnel, listTunnels, stopTunnel API.
 */
export class TunnelManager extends Disposable implements ITunnelManager {
  private activeTunnels: Map<number, ITunnelConnection> = new Map();

  constructor(
    private config: ITunnelManagerConfig,
    private readonly logger: ILogger
  ) {
    super();
  }

  async createTunnel(
    config: ITunnelConfig,
  ): Promise<ITunnelConnection> {
    // Create tunnel connection with proxy stream factory
    const tunnelConfig: ITunnelConfig = {
      runId: config.runId,
      remotePort: config.remotePort,
      localPort: config.localPort,
      region: config.region,
      direct: config.direct,
      ssh: config.ssh,
    };

    // Get connection info from Cloud Pipeline API
    // Corresponds to pipe-cli: conn_info = get_conn_info(run_id, region)
    this.logger.debug(`Fetching connection info for run ${config.runId}...`);
    const runConnInfo = await getRunConnectionInfo(
      config.runId,
      config.region,
      this.config.api,
      this.logger,
    );

    // Build proxy endpoint (corresponds to pipe-cli proxy_endpoint)
    // pipe-cli: proxy_endpoint = (os.getenv('CP_CLI_TUNNEL_PROXY_HOST', conn_info.ssh_proxy[0]), ...)


    const edgeProxyEndpoint: ProxyEndpoint = {
      host: process.env.CP_CLI_TUNNEL_PROXY_HOST || runConnInfo.sshProxy.host,
      port: process.env.CP_CLI_TUNNEL_PROXY_PORT
        ? parseInt(process.env.CP_CLI_TUNNEL_PROXY_PORT, 10)
        : runConnInfo.sshProxy.port,
      username: runConnInfo.owner,
      password: this.config.api.token,
    };

    const connection = new TunnelConnection(
      config.runId,
      tunnelConfig,
      async () => {
        // Build target endpoint (corresponds to pipe-cli target_endpoint)
        // pipe-cli: target_endpoint = (conn_info.ssh_endpoint[0], remote_port)
        const targetEndpoint: Endpoint = {
          host: runConnInfo.sshEndpoint.host,  // Now using actual pod IP from conn_info
          port: tunnelConfig.remotePort,
        };

        this.logger.debug(
          `Creating proxy tunnel: ${edgeProxyEndpoint.host}:${edgeProxyEndpoint.port} -> ${targetEndpoint.host}:${targetEndpoint.port}`
        );

        // Factory function to create proxy stream on demand
        return httpProxyTunnelConnect(
          this.config.proxy ?? edgeProxyEndpoint,
          targetEndpoint,
          this.config.connectionTimeout,
          this.logger,
        );
      },
      this.logger,
    );

    this._register(connection);
    this.activeTunnels.set(config.runId, connection);

    return connection;
  }

  async listTunnels(): Promise<ITunnelInfo[]> {
    this.logger.debug("Listing active tunnels");
    return findExistingTunnels(this.logger);
  }

  async stopTunnel(runId?: number, localPort?: number): Promise<void> {
    if (runId !== undefined) {
      const conn = this.activeTunnels.get(runId);
      if (conn) {
        this.logger.info(`Stopping tunnel for run ${runId}`);
        conn.dispose();
        this.activeTunnels.delete(runId);
      }
    } else if (localPort !== undefined) {
      this.logger.info(`Stopping tunnel on local port ${localPort}`);
      // TODO: Find and stop tunnel by local port
    } else {
      this.logger.warn("stopTunnel called without runId or localPort");
    }
  }

  override dispose(): void {
    this.logger.info("Disposing tunnel manager");
    this.activeTunnels.forEach((conn) => conn.dispose());
    this.activeTunnels.clear();
    super.dispose();
  }
}
