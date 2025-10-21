import { PipeTunnelInfo } from "..";
import { Disposable } from "../../common/disposable";

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
}
