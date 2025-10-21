import * as vscode from "vscode";
import * as path from "path";
// import SSHConfiguration, { getSSHConfigPath } from './ssh/sshConfig';
import { RemoteLocationHistory } from "./remoteLocationHistory";
import { Disposable } from "./common/disposable";
import {
  addNewHost,
  openRemoteCpLocationWindow,
  openRemoteCpWindow,
  openSSHConfigFile,
} from "./commands-handlers";
import SSHDestination from "./ssh/sshDestination";
import { ILogger } from "./common/logger";
import { RunInfo, RunLocation } from "./cp-client";
import { Commands } from "./commands";
import { OnStartAction, OnStartWhen } from "./cp-ext/on-start";
import { CpExtension } from "./cp-ext";

export function registerHostTreeView(cpExt: CpExtension): void {
  const locationHistory = new RemoteLocationHistory(cpExt.context);
  const hostTreeDataProvider = new HostTreeDataProvider(
    cpExt,
    locationHistory,
    cpExt.logger,
  );
  cpExt.context.subscriptions.push(
    vscode.window.createTreeView(
      "cloudPipelineHosts" /* registered with package.json/contributes/views/remote */,
      {
        treeDataProvider: hostTreeDataProvider,
      },
    ),
    hostTreeDataProvider,
  );
}

class OwnerInfo extends Object {
  constructor(
    public readonly owner: string,
    public readonly runs: RunInfo[],
    public readonly isTokenOwner: boolean,
  ) {
    super();
  }
}

type DataTreeItem = OwnerInfo | RunInfo | RunLocation;

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

export class HostTreeDataProvider
  extends Disposable
  implements vscode.TreeDataProvider<DataTreeItem>
{
  private static objCounter = 0;
  private objId = HostTreeDataProvider.objCounter++;

  protected toLog(): string {
    return `${this.constructor.name}<${this.objId}>`;
  }

  private readonly _onDidChangeTreeData = this._register(
    new vscode.EventEmitter<DataTreeItem | DataTreeItem[] | void>(),
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
    registerCommand(Commands.explorer.pipeUpdate, () => this.pipeUpdate());
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

    this._register(
      vscode.workspace.onDidChangeConfiguration((e) => {
        if (e.affectsConfiguration("remote.SSH.configFile")) {
          this.refresh();
        }
      }),
    );
  }

  getTreeItem(element: DataTreeItem): vscode.TreeItem {
    const logPfx = `${this.toLog()}.getTreeItem()`;
    if (element instanceof RunLocation) {
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
      treeItem.collapsibleState = ownerInfo.isTokenOwner
        ? vscode.TreeItemCollapsibleState.Expanded
        : vscode.TreeItemCollapsibleState.Collapsed;
      treeItem.iconPath = new vscode.ThemeIcon("account");
      treeItem.contextValue = ownerInfo.owner;
      return treeItem;
    } else
      throw new Error(
        `${logPfx}, unexpected tree item\n` +
          // `  type '${element ? element.constructor.name : "nothing"}'\n` +
          `  value ${JSON.stringify(element)}'.`,
      );
  }

  async getChildren(element?: DataTreeItem): Promise<DataTreeItem[]> {
    if (!element) {
      return await this.getRoot();
    } else if (element instanceof OwnerInfo) {
      return element.runs;
    } else if (element instanceof RunInfo) {
      return element.locations ?? [];
    } else return [];
  }

  async getRoot(): Promise<DataTreeItem[]> {
    const runList = await this.cpClient.getRunList();

    const resItemList: DataTreeItem[] = [];
    const byOwners: { [owner: string]: RunInfo[] } = {};

    for (const runInfo of runList) {
      let ownerRuns = byOwners[runInfo.owner];
      if (!ownerRuns) {
        ownerRuns = byOwners[runInfo.owner] = [];
      }
      ownerRuns.push(runInfo);
    }

    const tokenOwner = (await this.cpClient.getVersion()).tokenOwner;
    const byOwnersSorted = Object.entries(byOwners).sort((a, b) =>
      a[0].localeCompare(b[0]),
    );
    for (const [owner, ownerRuns] of byOwnersSorted) {
      const item = new OwnerInfo(owner, ownerRuns, owner === tokenOwner);
      if (owner == tokenOwner) {
        resItemList.unshift(item);
      } else {
        resItemList.push(item);
      }
    }
    return resItemList;
  }

  private pipeUpdate() {
    void this.cpClient
      .ensurePipeExec(true)
      .catch((err) => {
        this.logger.error("Failed to update pipe client:\n" + err);
        vscode.window.showErrorMessage(`Failed to update pipe client: ${err}`);
      })
      .then(() => {
        vscode.window.showInformationMessage(
          "Pipe client updated successfully.",
        );
        this.refresh();
      });
  }

  private refresh() {
    this._onDidChangeTreeData.fire();
  }

  private async deleteHostLocation(runLocation: RunLocation) {
    await this.locationHistory.removeLocation(
      `pipeline-${runLocation.run.runId}`,
      runLocation.path,
    );
    this.refresh();
  }

  private async openRemoteCpWindow(element: RunInfo, reuseWindow: boolean) {
    const logPfx = `${this.toLog()}.openRemoteCpWindow()`;
    const sshDest = new SSHDestination(`pipeline-${element.runId}`);
    this.cpExtConfig.onStart.push({
      when: OnStartWhen.onDidResolve,
      action: OnStartAction.openFolder,
    });
    await this.cpExtConfig.save(logPfx);
    openRemoteCpWindow(sshDest.toEncodedString(), reuseWindow);
  }

  private async openRemoteCpLocationWindow(
    element: RunLocation,
    reuseWindow: boolean,
  ) {
    const sshDest = new SSHDestination(`pipeline-${element.run.runId}`);
    openRemoteCpLocationWindow(
      sshDest.toEncodedString(),
      element.path,
      reuseWindow,
    );
  }
}
