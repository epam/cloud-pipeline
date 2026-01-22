import * as vscode from "vscode";

import { PipeTunnelInfo } from "..";
import { quickPickWithCountdown } from "../../common/quick-pick-with-countdown";

export abstract class PipeTunnelItem implements vscode.QuickPickItem {
  protected constructor(public readonly label: string) {}
}

export class EnterLocalPortItem extends PipeTunnelItem {
  constructor() {
    super("Enter local port manually");
  }
}

export class CreateTunnelOnLocalPortItem extends PipeTunnelItem {
  constructor() {
    super(
      "Create tunnel within VSCode (on local port available)",
    );
  }
}

export class CreateTunnelInternalItem extends PipeTunnelItem {
  constructor() {
    super("Create tunnel within VSCode (internal)");
  }
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

  const enterPortItem = new EnterLocalPortItem();
  const createOnPortItem = new CreateTunnelOnLocalPortItem();
  const createInternalItem = new CreateTunnelInternalItem();
  const execReusableTunnelItem = new ExecutePipeTunnelItem(
    "Execute new tunnel (reusable)",
    false,
  );
  const execBoundTunnelItem = new ExecutePipeTunnelItem(
    "Execute new tunnel (bound)",
    true,
  );

  // Default to "Create tunnel within VSCode (on local port available)" with 10s timeout
  // Note: reorder so that CreateTunnelOnLocalPortItem is first (auto-selected on timeout)
  const choicesWithDefault: (PipeTunnelItem | vscode.QuickPickItem)[] = [
    createOnPortItem,
    enterPortItem,
    createInternalItem,
    { label: "-", kind: vscode.QuickPickItemKind.Separator },
    ...reuseChoices,
    { label: "-", kind: vscode.QuickPickItemKind.Separator },
    execReusableTunnelItem,
    execBoundTunnelItem,
  ];

  const userResp = await quickPickWithCountdown(
    `Pick a tunnel to connect run ${runId}`,
    choicesWithDefault as any,
    10000, // 10 second timeout - auto-selects first item (createOnPortItem)
  ).result;

  return userResp;
}