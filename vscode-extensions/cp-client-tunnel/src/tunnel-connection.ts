import { ITunnelConnection } from "./types";
import { Disposable, ITunnelConfig, ILogger, LoggerBase } from "cp-client-common";
import { Duplex } from "stream";
import { TcpForwarder } from "./tcp-forwarder";

/**
 * Represents an active tunnel connection.
 * Manages lifecycle and provides access to underlying socket/stream.
 */
export class TunnelConnection extends Disposable implements ITunnelConnection {
  readonly runId: number;
  readonly remotePort: number;
  localPort?: number;
  readonly pid?: number;
  readonly owner?: string;

  private proxyStream?: Duplex;
  private streamInitialized = false;
  private tcpForwarder?: TcpForwarder;

  constructor(
    runId: number,
    config: ITunnelConfig,
    private readonly getProxyStream: () => Promise<Duplex>,
    private readonly logger: ILogger,
  ) {
    super();
    this.runId = runId;
    this.remotePort = config.remotePort;
    this.localPort = config.localPort;
    this.logger = logger || new LoggerBase();

    // Start TCP forwarder if localPort is specified
    if (config.localPort !== undefined) {
      this.tcpForwarder = new TcpForwarder(
        config.localPort,
        this.getProxyStream,
        this.logger,
      );

      // Start listening asynchronously (don't wait in constructor)
      this.tcpForwarder.start()
        .then((actualPort) => {
          this.localPort = actualPort;
        })
        .catch((err) => {
          this.logger.error(`Failed to start TCP forwarder for runId=${runId}: ${err}`);
        });
    }
  }

  /**
   * Get the underlying stream (post-HTTP CONNECT, pre-SSH).
   * Lazily initialized on first call.
   */
  async getStream(): Promise<Duplex> {
    if (!this.streamInitialized) {
      this.proxyStream = await this.getProxyStream();
      this.streamInitialized = true;
    }
    return this.proxyStream!;
  }

  override dispose(): void {
    // Stop TCP forwarder if running
    if (this.tcpForwarder) {
      this.tcpForwarder.stop()
        .catch((err) => {
          this.logger.error(`Error stopping TCP forwarder for runId=${this.runId}: ${err}`);
        });
    }

    if (this.proxyStream) {
      this.proxyStream.destroy();
    }
    super.dispose();
  }
}
