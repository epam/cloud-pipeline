import { PipeTunnelInfo } from "..";
import { PipeTunnelBase } from "./pipe-tunnel-base";

export class ReusedPipeTunnel extends PipeTunnelBase {
  override get toStop(): boolean {
    return false;
  }

  constructor(private readonly tunnelInfo: PipeTunnelInfo) {
    super(tunnelInfo.runId, tunnelInfo.localPort);
  }

  override getInfo(): PipeTunnelInfo {
    return this.tunnelInfo;
  }
}
