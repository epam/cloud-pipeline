/**
 * Wrapper to integrate cp-client-tunnel as PipeTunnelBase implementation in remote-cp.
 * Allows using Node.js tunnel library as alternative to Python CLI.
 */

import { ILogger } from "../../common/logger";
import { PipeTunnelBase } from "./pipe-tunnel-base";
import { PipeTunnelInfo } from "..";
import { ICpExtConfig } from "../../config";
import { Duplex } from "stream";
import { ITunnelManagerConfig, parseProxyUrl } from "cp-client-tunnel";

// Import types from cp-client-tunnel library
import { TunnelManager, ITunnelConnection } from "cp-client-tunnel";
import { IApiOptions } from "cp-client-api";



function createTunnelManagerConfig(
  config: ICpExtConfig
): ITunnelManagerConfig {

  const api: IApiOptions = {
    url: config.pipeApiUri!,
    token: config.pipeApiToken!,
  };

  const envProxyUrl = process.env.CP_PROXY_URL
    || undefined;

  const proxy = parseProxyUrl(
    envProxyUrl,
    () => {
      return {
        username: process.env.CP_PROXY_USERNAME,
        password: process.env.CP_PROXY_PASSWORD
      }
    });

  return {
    api,
    proxy,
    connectionTimeout: 30,
  }
}

/**
 * Tunnel connection wrapper for Node.js implementation.
 * Manages tunnel lifecycle using cp-client-tunnel library.
 */
export class NodeJSTunnelClient extends PipeTunnelBase {
  private tunnelManager?: TunnelManager;
  private tunnelConnection?: ITunnelConnection;
  private toStopValue: boolean;

  constructor(
    runId: number,
    localPort: number,
    toStop: boolean,
    private readonly cpConfig: ICpExtConfig,
    private readonly logger: ILogger,
  ) {
    super(runId, localPort);
    this.toStopValue = toStop;
  }

  get toStop(): boolean {
    return this.toStopValue;
  }

  getInfo(): PipeTunnelInfo {
    return new PipeTunnelInfo(
      this.tunnelConnection?.pid || process.pid,
      null, // parentPid
      this.tunnelConnection?.owner || process.env.USER || process.env.USERNAME || "unknown",
      this.runId,
      this.localPort,
      22, // Remote port is always SSH
    );
  }

  /**
   * Get stream for SSH connection (for authResolver proxy mode).
   * Returns raw socket after HTTP CONNECT handshake.
   */
  override async getStream(): Promise<Duplex | null> {
    if (!this.tunnelConnection) {
      this.logger.warn(`NodeJSTunnelClient: No active connection for run ${this.runId}`);
      return null;
    }

    try {
      return await this.tunnelConnection.getStream();
    } catch (err) {
      this.logger.error(`NodeJSTunnelClient: Failed to get stream for run ${this.runId}`, err);
      return null;
    }
  }

  async activate(): Promise<void> {
    this.logger.info(`Activating Node.js tunnel for run ${this.runId}, localPort: ${this.localPort}`);

    try {
      // Get CP configuration (API URL, token, etc.)
      const cpClientConfig = await this.cpConfig.getClientConfig();
      if (!cpClientConfig) {
        throw new Error("Cloud Pipeline configuration not found");
      }

      const config = createTunnelManagerConfig(this.cpConfig);

      // Initialize TunnelManager
      this.tunnelManager = new TunnelManager(config, this.logger);

      // Create tunnel
      this.tunnelConnection = await this.tunnelManager.createTunnel({
        runId: this.runId,
        remotePort: 22,
        localPort: this.localPort === -1 ? undefined : this.localPort,
      });

      this.logger.info(`Node.js tunnel activated for run ${this.runId}`);
    } catch (err) {
      this.logger.error(`Failed to activate Node.js tunnel for run ${this.runId}`, err);
      throw err;
    }
  }

  async deactivate(): Promise<void> {
    this.logger.info(`Deactivating Node.js tunnel for run ${this.runId}`);

    try {
      if (this.tunnelConnection) {
        await this.tunnelManager?.stopTunnel(this.runId);
        this.tunnelConnection = undefined;
      }
    } catch (err) {
      this.logger.error(`Failed to deactivate Node.js tunnel for run ${this.runId}`, err);
    }
  }

  override dispose(): void {
    this.logger.info(`Disposing Node.js tunnel for run ${this.runId}`);

    // Cleanup tunnel connection
    if (this.tunnelConnection) {
      this.tunnelConnection.dispose();
      this.tunnelConnection = undefined;
    }

    this.tunnelManager = undefined;
    super.dispose();
  }
}
