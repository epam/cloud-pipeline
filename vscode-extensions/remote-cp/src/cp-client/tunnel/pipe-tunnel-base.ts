import { PipeTunnelInfo } from "..";
import { Disposable } from "../../common/disposable";
import { Duplex } from "stream";

export interface PipeTunnelChild {
  readonly pid: number;
}

export abstract class PipeTunnelBase extends Disposable {
  private static objCounter = 0;
  private objId = PipeTunnelBase.objCounter++;

  protected toLog(): string {
    return `${this.constructor.name}<${this.objId}, run: ${this.runId}, lp: ${this.localPort}>`;
  }

  public abstract get toStop(): boolean;

  constructor(
    public runId: number,
    public localPort: number,
  ) {
    super();
  }

  public abstract getInfo(): PipeTunnelInfo;

  /**
   * Returns a stream for direct tunnel connection (internal mode).
   * This is used by authResolver to establish SSH connection through the tunnel.
   * @returns Duplex stream if tunnel supports internal mode, null otherwise
   */
  public async getStream(): Promise<Duplex | null> {
    return null; // Default implementation - no stream support
  }
}
