import * as vscode from "vscode";
import * as cp from "child_process";

import { PipeTunnelInfo } from "..";
import { quickPickWithCountdown } from "../../common/quick-pick-with-countdown";

export abstract class PipeTunnelItem implements vscode.QuickPickItem {
  protected constructor(public readonly label: string) {}
}

export class ReusePipeTunnelItem extends PipeTunnelItem {
  constructor(public readonly tunnelInfo: PipeTunnelInfo) {
    const label =
      `run: ${tunnelInfo.runId}, ` +
      `lp: ${tunnelInfo.localPort}, rp: ${tunnelInfo.remotePort}, ` +
      `pid: ${tunnelInfo.pid}`;
    super(label);
  }
}

export class ExecutePipeTunnelItem extends PipeTunnelItem {
  constructor(
    label: string,
    public readonly toStop: boolean,
  ) {
    super(label);
  }
}

export async function askUserForPipeTunnel(
  runId: number,
  tunnelList: PipeTunnelInfo[],
): Promise<PipeTunnelItem> {
  const reuseChoices: ReusePipeTunnelItem[] = tunnelList.map((ti) => {
    return new ReusePipeTunnelItem(ti);
  });

  const execReusableTunnelItem = new ExecutePipeTunnelItem(
    "Execute new tunnel (reusable)",
    false,
  );
  const execBoundTunnelItem = new ExecutePipeTunnelItem(
    "Execute new tunnel (bound)",
    true,
  );
  const choices = (reuseChoices as vscode.QuickPickItem[]).concat([
    { label: "-", kind: vscode.QuickPickItemKind.Separator },
    execReusableTunnelItem,
    execBoundTunnelItem,
  ]);

  const userResp = await quickPickWithCountdown(
    `Pick a tunnel to connect run ${runId}`,
    choices,
    15000,
  );

  return userResp;
}
