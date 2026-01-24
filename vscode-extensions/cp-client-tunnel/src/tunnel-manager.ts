import { ITunnelConnection, ITunnelManager } from "./interfaces";
import { Disposable, ILogger, LoggerBase, ITunnelConfig, ITunnelInfo } from "cp-client-common";
import { TunnelConnection } from "./tunnel-connection";
import { httpProxyTunnelConnect } from "./proxy";
import { findExistingTunnels } from "./process-discovery";

export interface TunnelManagerConfig {
  proxyHost: string;
  proxyPort: number;
  proxyUsername?: string;
  proxyPassword?: string;
  connectionTimeout?: number;
  logger?: ILogger;
}

/**
 * Main tunnel manager implementation.
 * Provides startTunnel, listTunnels, stopTunnel API.
 */
export class TunnelManager extends Disposable implements ITunnelManager {
  private logger: ILogger;
  private activeTunnels: Map<number, ITunnelConnection> = new Map();

  constructor(private config: TunnelManagerConfig) {
    super();
    this.logger = config.logger || new LoggerBase();
  }

  async startTunnel(
    runId: number,
    config: ITunnelConfig,
  ): Promise<ITunnelConnection> {
    this.logger.info(`Starting tunnel to run ${runId}`);

    // Create tunnel connection with proxy stream factory
    const tunnelConfig: ITunnelConfig = {
      runId,
      remotePort: config.remotePort,
      localPort: config.localPort,
      region: config.region,
      direct: config.direct,
      ssh: config.ssh,
    };

    const connection = new TunnelConnection(
      runId,
      tunnelConfig,
      async () => {
        // Factory function to create proxy stream on demand
        return httpProxyTunnelConnect(
          this.config.proxyHost,
          this.config.proxyPort,
          "127.0.0.1", // Target host (will be overridden by caller)
          tunnelConfig.remotePort,
          this.config.proxyUsername,
          this.config.proxyPassword,
          this.config.connectionTimeout,
          this.logger,
        );
      },
    );

    this._register(connection);
    this.activeTunnels.set(runId, connection);

    this.logger.info(`Tunnel to run ${runId} started (connection created)`);
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
