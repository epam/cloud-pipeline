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
import { ILogger, Logger } from "./common/logger";
import { CloudPipelineClient, RunInfo, RunLocation } from "./cp-client";
import { Commands } from "./commands";

export function registerHostTreeView(
  context: vscode.ExtensionContext,
  logger: Logger,
): void {
  const locationHistory = new RemoteLocationHistory(context);
  const hostTreeDataProvider = new HostTreeDataProvider(
    locationHistory,
    logger,
  );
  context.subscriptions.push(
    vscode.window.createTreeView(
      "cloudPipelineHosts" /* registered with package.json/contributes/views/remote */,
      {
        treeDataProvider: hostTreeDataProvider,
      },
    ),
    hostTreeDataProvider,
  );
}

type DataTreeItem = RunInfo | RunLocation;

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
  private readonly cpClient;

  private readonly _onDidChangeTreeData = this._register(
    new vscode.EventEmitter<DataTreeItem | DataTreeItem[] | void>(),
  );
  public readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  constructor(
    private locationHistory: RemoteLocationHistory,
    private logger: ILogger,
  ) {
    super();

    this.cpClient = new CloudPipelineClient(this.logger);

    const registerCommand = (
      command: string,
      commandHandler: (...args: any[]) => any,
    ) => {
      this._register(vscode.commands.registerCommand(command, commandHandler));
    };

    registerCommand(HostTreeEvent.add, () => addNewHost());
    registerCommand(HostTreeEvent.configure, () => openSSHConfigFile());
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
    // this._register(vscode.workspace.onDidSaveTextDocument(e => {
    //     if (e.uri.fsPath === getSSHConfigPath()) {
    //         this.refresh();
    //     }
    // }));
  }

  getTreeItem(element: DataTreeItem): vscode.TreeItem {
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
      const treeItem = new vscode.TreeItem(`pipeline-${runInfo.runId}`);
      treeItem.collapsibleState = element.locations?.length
        ? vscode.TreeItemCollapsibleState.Collapsed
        : vscode.TreeItemCollapsibleState.None;
      treeItem.iconPath = new vscode.ThemeIcon("vm");
      treeItem.contextValue = HostTreeItemContext.host;
      return treeItem;
    } else throw new Error(`Unknown element type ${element}`);
  }

  async getChildren(element?: DataTreeItem): Promise<DataTreeItem[]> {
    if (!element) {
      // const sshConfigFile = await SSHConfiguration.loadFromFS();
      // const hosts = sshConfigFile.getAllConfiguredHosts();
      // return hosts.map(hostname => new HostItem(hostname, this.locationHistory.getHistory(hostname)));
      const runList: RunInfo[] = await this.cpClient.getRunList();
      return runList;
    } else if (element instanceof RunInfo) {
      return element.locations ?? [];
    } else return [];
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
    const sshDest = new SSHDestination(`pipeline-${element.runId}`);
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
