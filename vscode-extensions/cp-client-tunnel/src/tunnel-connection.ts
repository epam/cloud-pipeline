import { ITunnelConnection } from "./interfaces";
import { Disposable, ITunnelConfig } from "cp-client-common";
import { Duplex } from "stream";

/**
 * Represents an active tunnel connection.
 * Manages lifecycle and provides access to underlying socket/stream.
 */
export class TunnelConnection extends Disposable implements ITunnelConnection {
  readonly runId: number;
  readonly remotePort: number;
  readonly localPort?: number;
  readonly pid?: number;
  readonly owner?: string;

  private proxyStream?: Duplex;
  private streamInitialized = false;

  constructor(
    runId: number,
    config: ITunnelConfig,
    private readonly getProxyStream: () => Promise<Duplex>,
  ) {
    super();
    this.runId = runId;
    this.remotePort = config.remotePort;
    this.localPort = config.localPort;
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
    if (this.proxyStream) {
      this.proxyStream.destroy();
    }
    super.dispose();
  }
}
