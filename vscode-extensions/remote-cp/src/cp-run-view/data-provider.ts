import * as vscode from "vscode";
import * as path from "path";

// import SSHConfiguration, { getSSHConfigPath } from './ssh/sshConfig';
import { RemoteLocationHistory } from "../remoteLocationHistory";
import { Disposable } from "../common/disposable";
import {
  addNewHost,
  openRemoteCpLocationWindow,
  openRemoteCpWindow,
  openSSHConfigFile,
} from "../commands-handlers";
import SSHDestination from "../ssh/sshDestination";
import { ILogger } from "../common/logger";
import { RunInfo, RunLocation as LocationInfo } from "../cp-client";
import { Commands } from "../commands";
import { OnStartAction, OnStartWhen } from "../cp-ext/on-start";
import { CpExtension } from "../cp-ext";
import { UserCancelledError } from "../cp-client/error";

class OwnerInfo extends Object {
  constructor(
    public readonly owner: string,
    public readonly runs: RunInfo[],
    public readonly isTokenOwner: boolean,
  ) {
    super();
  }
}

export type ItemInfo = OwnerInfo | RunInfo | LocationInfo;

export enum HostTreeEvent {
  add = "remote-cp.explorer.add",
  configure = "openremotessh.explorer.configure",
  reopenFolderInNewWindow = "openremotessh.explorer.reopenFolderInNewWindow",
  reopenFolderInCurrentWindow = "openremotessh.explorer.reopenFolderInCurrentWindow",
  deleteFolderHistoryItem = "openremotessh.explorer.deleteFolderHistoryItem",
}

export enum HostTreeItemContext {
  host = "remote-cp.explorer.host",
  folder = "remote-cp.explorer.folder",
}

export class CpRunTreeDataProvider
  extends Disposable
  implements vscode.TreeDataProvider<ItemInfo>
{
  private static objCounter = 0;
  private objId = CpRunTreeDataProvider.objCounter++;

  protected toLog(): string {
    return `${this.constructor.name}<${this.objId}>`;
  }

  private readonly _onDidChangeTreeData = this._register(
    new vscode.EventEmitter<ItemInfo | ItemInfo[] | void>(),
  );
  public readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  protected get cpClient() {
    return this.cpExt.cpClient!;
  }

  protected get cpExtConfig() {
    return this.cpExt.cpExtConfig;
  }

  constructor(
    private readonly cpExt: CpExtension,
    private locationHistory: RemoteLocationHistory,
    private logger: ILogger,
  ) {
    super();

    const registerCommand = (
      command: string,
      commandHandler: (...args: any[]) => any,
    ) => {
      this._register(vscode.commands.registerCommand(command, commandHandler));
    };

    registerCommand(HostTreeEvent.add, () => addNewHost());
    registerCommand(HostTreeEvent.configure, () => openSSHConfigFile());
    registerCommand(Commands.explorer.pipeUpdate, () => {
      this.updatePipeClient();
    });
    registerCommand(Commands.explorer.refresh, () => this.refresh());
    registerCommand(Commands.explorer.emptyWindowInNewWindow, (e) =>
      this.openRemoteCpWindow(e, false),
    );
    registerCommand(Commands.explorer.emptyWindowInCurrentWindow, (e) =>
      this.openRemoteCpWindow(e, true),
    );
    registerCommand(HostTreeEvent.reopenFolderInNewWindow, (e) =>
      this.openRemoteCpLocationWindow(e, false),
    );
    registerCommand(HostTreeEvent.reopenFolderInCurrentWindow, (e) =>
      this.openRemoteCpLocationWindow(e, true),
    );
    registerCommand(HostTreeEvent.deleteFolderHistoryItem, (e) =>
      this.deleteHostLocation(e),
    );
    registerCommand(Commands.explorer.filter, async () => {
      await this.filterRuns();
    });

    this._register(
      vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration("remote.SSH.configFile")) {
          this.refresh();
        }
      }),
    );
  }

  getTreeItem(element: ItemInfo): vscode.TreeItem {
    const logPfx = `${this.toLog()}.getTreeItem()`;
    // this.logger.debug(
    //   `${logPfx}, this.filterValue: ${this.filterValue}` +
    //     `  element: ${JSON.stringify(element)}`,
    // );
    if (element instanceof LocationInfo) {
      const label = path.posix
        .basename(element.path)
        .replace(/\.code-workspace$/, " (Workspace)");
      const treeItem = new vscode.TreeItem(label);
      treeItem.description = path.posix.dirname(element.path);
      treeItem.iconPath = new vscode.ThemeIcon("folder");
      treeItem.contextValue = HostTreeItemContext.folder;
      return treeItem;
    } else if (element instanceof RunInfo) {
      const runInfo = element as RunInfo;
      const runLabel = `${runInfo.pipeline} (${runInfo.runId})`;
      const treeItem = new vscode.TreeItem(runLabel);
      treeItem.collapsibleState = element.locations?.length
        ? vscode.TreeItemCollapsibleState.Collapsed
        : vscode.TreeItemCollapsibleState.None;
      treeItem.iconPath = new vscode.ThemeIcon("vm");
      treeItem.contextValue = HostTreeItemContext.host;
      return treeItem;
    } else if (element instanceof OwnerInfo) {
      const ownerInfo = element as OwnerInfo;
      const treeItem = new vscode.TreeItem(ownerInfo.owner);
      treeItem.collapsibleState =
        ownerInfo.isTokenOwner || this.filterValue != null
          ? vscode.TreeItemCollapsibleState.Expanded
          : vscode.TreeItemCollapsibleState.Collapsed;
      treeItem.iconPath = new vscode.ThemeIcon("account");
      treeItem.contextValue = ownerInfo.owner;
      this.logger.debug(
        `${logPfx}, this.filterValue: ${this.filterValue}` +
          `  element: ${JSON.stringify(treeItem)}`,
      );
      return treeItem;
    } else
      throw new Error(
        `${logPfx}, unexpected tree item\n` +
          // `  type '${element ? element.constructor.name : "nothing"}'\n` +
          `  value ${JSON.stringify(element)}'.`,
      );
  }

  async getChildren(element?: ItemInfo): Promise<ItemInfo[]> {
    if (!element) {
      return await this.getRoot();
    } else if (element instanceof OwnerInfo) {
      return element.runs;
    } else if (element instanceof RunInfo) {
      return element.locations ?? [];
    } else return [];
  }

  private _items: OwnerInfo[] | null = null;

  async getRoot(): Promise<ItemInfo[]> {
    if (this._items == null) {
      this._items = [];
      const byOwners: { [owner: string]: RunInfo[] } = {};

      const runList = await this.cpClient.getRunList();
      for (const runInfo of runList) {
        let ownerRuns = byOwners[runInfo.owner];
        if (!ownerRuns) {
          ownerRuns = byOwners[runInfo.owner] = [];
        }
        if (
          this.filterValue == null ||
          runInfo.pipeline.includes(this.filterValue) ||
          runInfo.runId.toString().includes(this.filterValue) ||
          runInfo.owner.includes(this.filterValue)
        ) {
          ownerRuns.push(runInfo);
        }
      }

      const tokenOwner = (await this.cpClient.getVersion()).tokenOwner;
      const byOwnersSorted = Object.entries(byOwners).sort((a, b) =>
        a[0].localeCompare(b[0]),
      );
      for (const [owner, ownerRuns] of byOwnersSorted) {
        const item = new OwnerInfo(owner, ownerRuns, owner === tokenOwner);
        if (owner == tokenOwner) {
          this._items.unshift(item);
        } else {
          this._items.push(item);
        }
      }
    }

    const resItems: ItemInfo[] = [];
    for (const oI of this._items) {
      const ownerRuns: RunInfo[] = [];
      for (const rI of oI.runs) {
        if (
          !this.filterValue ||
          rI.runId.toString().includes(this.filterValue) ||
          rI.pipeline.includes(this.filterValue)
        ) {
          const runItem = new RunInfo(
            rI.runId,
            rI.parentRunId,
            rI.pipeline,
            rI.version,
            rI.status,
            rI.started,
            rI.owner,
          );
          ownerRuns.push(runItem);
        }
      }
      if (
        !this.filterValue ||
        ownerRuns.length > 0 ||
        oI.owner.includes(this.filterValue ?? "")
      ) {
        const ownerItem = new OwnerInfo(oI.owner, ownerRuns, oI.isTokenOwner);
        resItems.push(ownerItem);
      }
    }
    return resItems;
  }

  /**
   * Update the pipe client (executable)
   */
  private updatePipeClient() {
    if (this.cpExt.codeContext.isUpdatingPipeClient) {
      vscode.window.showWarningMessage(
        "Pipe client update is already in progress.",
      );
      return;
    }

    void this.cpClient
      .ensurePipeExec(true)
      .then(() => {
        vscode.window.showInformationMessage(
          "Pipe client updated successfully.",
        );
        this.refresh();
      })
      .catch((err) => {
        const errMsg = `Failed to update the pipe client: ${err}`;
        if (err instanceof UserCancelledError) {
          this.logger.warn(errMsg);
          vscode.window.showWarningMessage(errMsg);
        } else {
          this.logger.error(errMsg);
          this.logger.error(err);
          vscode.window.showErrorMessage(errMsg);
        }
      });
  }

  private refresh(reload: boolean = true) {
    if (reload) this._items = null;
    this._onDidChangeTreeData.fire();
  }

  private async deleteHostLocation(runLocation: LocationInfo) {
    await this.locationHistory.removeLocation(
      `pipeline-${runLocation.run.runId}`,
      runLocation.path,
    );
    this.filterChanged = true;
    this.refresh();
  }

  private async openRemoteCpWindow(element: RunInfo, reuseWindow: boolean) {
    const logPfx = `${this.toLog()}.openRemoteCpWindow()`;
    const sshDest = new SSHDestination(`pipeline-${element.runId}`);
    const onStartV = await this.cpExtConfig.getOnStart();
    await this.cpExtConfig.setOnStart([
      ...onStartV,
      {
        when: OnStartWhen.onDidResolve,
        action: OnStartAction.openFolder,
      },
    ]);
    await this.cpExtConfig.save(logPfx);
    openRemoteCpWindow(sshDest.toEncodedString(), reuseWindow);
  }

  private async openRemoteCpLocationWindow(
    element: LocationInfo,
    reuseWindow: boolean,
  ) {
    const sshDest = new SSHDestination(`pipeline-${element.run.runId}`);
    openRemoteCpLocationWindow(
      sshDest.toEncodedString(),
      element.path,
      reuseWindow,
    );
  }

  // -- filter --

  private filterChanged: boolean = false;

  private _filterValue: string | null = null;
  public get filterValue(): string | null {
    return this._filterValue;
  }

  public set filterValue(value: string | null) {
    if (this._filterValue !== value) {
      this._filterValue = value;
      this.refresh(false);
    }
  }

  async filterRuns(): Promise<void> {
    const input = await vscode.window.createInputBox();
    input.value = ""; // this.filterValue ?? "";
    input.placeholder = "Filter runs...";

    input.onDidChangeValue(() => {
      this.filterValue = input.value;
    });

    input.show();
    this.filterValue = await new Promise<string | null>((resolve) => {
      let accepted: boolean = false;
      input.onDidAccept(() => {
        accepted = true;
        input.hide();
      });
      input.onDidHide(() => {
        resolve(accepted ? input.value : null);
      });
    });
  }
}
