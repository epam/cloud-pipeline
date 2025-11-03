import * as vscode from "vscode";

import { CpExtension } from "../cp-ext";
import { ILogger } from "../common/logger";

interface FilterableTreeDataProvider<T> extends vscode.TreeDataProvider<T> {
  filterValue: string | null;
}

type TreeNode = {
  label: string;
  description?: string;
  tooltip?: string;
  iconId?: string;
  nodeId: string;
  expanded: boolean;
  hasChildren: boolean;
  actions?: TreeNodeAction[];
  contextMenu?: TreeNodeAction[];
  children: TreeNode[];
};

type TreeNodeAction = {
  command: string;
  title?: string;
  iconId?: string;
};

type WebviewMessage =
  | { command: "filterChanged"; text: string }
  | { command: "requestData" }
  | { command: "executeNodeCommand"; nodeId: string; action: string };

export class CpRunViewProvider<TItem> implements vscode.WebviewViewProvider {
  private static objCounter = 0;
  private objId = CpRunViewProvider.objCounter++;

  protected toLog(): string {
    return `${this.constructor.name}<${this.objId}>`;
  }

  private _view?: vscode.WebviewView;
  private nodeLookup: Map<string, TItem> = new Map();
  private nodeIdCounter = 0;
  private templatePromise?: Promise<string>;

  private readonly runViewHtmlUri: vscode.Uri;
  private readonly scriptUri: vscode.Uri;
  private readonly codiconCssUri: vscode.Uri;

  protected get logger(): ILogger {
    return this.cpExt.logger;
  }

  constructor(
    private readonly cpExt: CpExtension,
    private readonly dataProvider: vscode.TreeDataProvider<TItem>,
  ) {
    const logPfx = `${this.toLog()}.constructor()`;
    this.runViewHtmlUri = vscode.Uri.joinPath(
      this.cpExt.context.extensionUri,
      "resources",
      "run-view",
      "run-view.html",
    );
    this.scriptUri = vscode.Uri.joinPath(
      this.cpExt.context.extensionUri,
      "dist",
      "webview",
      "cp-run-view.js",
    );
    this.codiconCssUri = vscode.Uri.joinPath(
      this.cpExt.context.extensionUri,
      "dist",
      "webview",
      "codicon",
      "codicon.css",
    );
    // this.codiconCssUri = vscode.Uri.joinPath(
    //   this.cpExt.context.extensionUri,
    //   "node_modules",
    //   "@vscode",
    //   "codicons",
    //   "dist",
    //   "codicon.css",
    // );

    this.logger.debug(
      `${logPfx} run-view.html\n` + `  uri: ${this.runViewHtmlUri.fsPath}`,
    );
    this.logger.debug(
      `${logPfx} run-view.js \n` + `  uri: ${this.scriptUri.fsPath}`,
    );
    this.logger.debug(
      `${logPfx} codicon.css\n` + `  uri: ${this.codiconCssUri.fsPath}`,
    );
  }

  resolveWebviewView(
    webviewView: vscode.WebviewView,
    _context: vscode.WebviewViewResolveContext,
    _token: vscode.CancellationToken,
  ) {
    this._view = webviewView;
    const webview = webviewView.webview;
    webview.options = { enableScripts: true };
    void this.initializeWebview(webviewView);

    const messageSubscription = webview.onDidReceiveMessage((message) =>
      this.onMessage(message as WebviewMessage),
    );
    const dataSubscription = this.dataProvider.onDidChangeTreeData
      ? this.dataProvider.onDidChangeTreeData(() => void this.update())
      : undefined;

    webviewView.onDidDispose(() => {
      messageSubscription.dispose();
      dataSubscription?.dispose();
    });
  }

  private async initializeWebview(
    webviewView: vscode.WebviewView,
  ): Promise<void> {
    try {
      webviewView.webview.html = await this.getHtml(webviewView.webview);
    } catch (error) {
      console.error("Failed to load run view HTML", error);
      webviewView.webview.html =
        "<html><body>Unable to load view.</body></html>";
    }
  }

  private async getHtml(webview: vscode.Webview): Promise<string> {
    const template = await this.getHtmlTemplate();
    const nonce = this.getNonce();
    const scriptUri = webview.asWebviewUri(this.scriptUri).toString();
    const codiconCssUri = webview.asWebviewUri(this.codiconCssUri).toString();
    const csp = [
      "default-src 'none';",
      `img-src ${webview.cspSource} https:;`,
      `script-src 'nonce-${nonce}' ${webview.cspSource};`,
      `style-src 'nonce-${nonce}' ${webview.cspSource};`,
      `font-src ${webview.cspSource};`,
    ].join(" ");

    return template
      .replace(/{{csp}}/g, csp)
      .replace(/{{nonce}}/g, nonce)
      .replace(/{{scriptUri}}/g, scriptUri)
      .replace(/{{codiconCssUri}}/g, codiconCssUri);
  }

  private getHtmlTemplate(): Promise<string> {
    if (!this.templatePromise) {
      this.templatePromise = Promise.resolve(
        vscode.workspace.fs.readFile(this.runViewHtmlUri),
      ).then((buffer) => Buffer.from(buffer).toString("utf8"));
    }
    return this.templatePromise;
  }

  private onMessage(msg: WebviewMessage) {
    if (!this._view) {
      return;
    }

    if (msg.command === "filterChanged") {
      if ("filterValue" in this.dataProvider) {
        const provider = this.dataProvider as FilterableTreeDataProvider<TItem>;
        provider.filterValue = msg.text ? msg.text : null;
      }
      void this.update();
    } else if (msg.command === "requestData") {
      void this.update();
    } else if (msg.command === "executeNodeCommand") {
      const target = this.nodeLookup.get(msg.nodeId);
      if (!target) {
        return;
      }
      void vscode.commands.executeCommand(msg.action, target);
    }
  }

  private async update() {
    if (!this._view) {
      return;
    }

    try {
      this.nodeLookup = new Map();
      this.nodeIdCounter = 0;
      const roots = (await this.dataProvider.getChildren()) ?? [];
      const data = await Promise.all(
        roots.map((item) => this.convertItem(item)),
      );
      await this._view.webview.postMessage({ command: "setData", data });
    } catch (err) {
      const errMsg = `Failed to update the run view: ${err}`;
      this.logger.error(errMsg);
      this.logger.error(err);
      vscode.window.showErrorMessage(errMsg);
    }
  }

  private async convertItem(item: TItem): Promise<TreeNode> {
    const treeItem = await this.dataProvider.getTreeItem(item);
    const collapsibleState =
      treeItem.collapsibleState ?? vscode.TreeItemCollapsibleState.None;
    const label = this.treeItemLabelToString(treeItem.label);
    const nodeId = this.registerNode(item);

    const hasChildren =
      collapsibleState === vscode.TreeItemCollapsibleState.Collapsed ||
      collapsibleState === vscode.TreeItemCollapsibleState.Expanded;
    const childItems = hasChildren
      ? await this.dataProvider.getChildren(item)
      : undefined;
    const children = childItems
      ? await Promise.all(
          childItems.map(async (child) => await this.convertItem(child)),
        )
      : [];

    const { inlineActions, contextMenu } = await this.buildActions(
      treeItem.contextValue,
      label,
      nodeId,
    );

    return {
      label,
      description: this.extractDescription(treeItem.description),
      tooltip: this.extractTooltip(treeItem.tooltip),
      iconId: this.extractIconId(treeItem.iconPath),
      nodeId,
      expanded: collapsibleState === vscode.TreeItemCollapsibleState.Expanded,
      hasChildren,
      actions: inlineActions,
      contextMenu,
      children,
    };
  }

  private treeItemLabelToString(label: vscode.TreeItem["label"]): string {
    if (!label) {
      return "";
    }
    if (typeof label === "string") {
      return label;
    }
    return label.label;
  }

  private registerNode(item: TItem): string {
    const nodeId = `node-${this.nodeIdCounter++}`;
    this.nodeLookup.set(nodeId, item);
    return nodeId;
  }

  private getNonce(): string {
    const possible =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    return Array.from({ length: 32 }, () =>
      possible.charAt(Math.floor(Math.random() * possible.length)),
    ).join("");
  }

  private async buildActions(
    contextValue: vscode.TreeItem["contextValue"],
    label: string,
    nodeId: string,
  ): Promise<{
    inlineActions?: TreeNodeAction[];
    contextMenu?: TreeNodeAction[];
  }> {
    const viewItem = this.resolveViewItemIdentifier(
      contextValue,
      label,
      nodeId,
    );
    if (!viewItem) {
      return {};
    }

    const pj = this.cpExt.cpExtConfig.packageJson;
    const [inlineCommands, navigationCommands] = await Promise.all([
      pj.getMenuCommands("view/item/context", viewItem, "inline"),
      pj.getMenuCommands("view/item/context", viewItem, "navigation"),
    ]);

    const inlineActions = inlineCommands.length
      ? inlineCommands.map((command) => ({
          command: command.command,
          title: command.title,
          iconId: this.extractIconFromCommand(command.icon),
        }))
      : undefined;
    const contextMenu = navigationCommands.length
      ? navigationCommands.map((command) => ({
          command: command.command,
          title: command.title,
          iconId: this.extractIconFromCommand(command.icon),
        }))
      : undefined;

    return { inlineActions, contextMenu };
  }

  private resolveViewItemIdentifier(
    contextValue: vscode.TreeItem["contextValue"],
    label: string,
    nodeId: string,
  ): string | undefined {
    if (typeof contextValue === "string" && contextValue.length > 0) {
      return contextValue;
    }

    if (Array.isArray(contextValue)) {
      const first = contextValue.find((value) => typeof value === "string");
      if (typeof first === "string") {
        return first;
      }
    }

    if (!label) {
      return undefined;
    }

    const sanitizedLabel = label.replace(/[^\w.-]/g, "-");
    return `${sanitizedLabel}:${nodeId}`;
  }

  private extractDescription(
    description: vscode.TreeItem["description"],
  ): string | undefined {
    if (!description) {
      return undefined;
    }
    if (typeof description === "string") {
      return description;
    }
    return undefined;
  }

  private extractTooltip(
    tooltip: vscode.TreeItem["tooltip"],
  ): string | undefined {
    if (!tooltip) {
      return undefined;
    }
    if (typeof tooltip === "string") {
      return tooltip;
    }
    if (tooltip instanceof vscode.MarkdownString) {
      return tooltip.value;
    }
    if (typeof tooltip === "object" && "value" in tooltip) {
      const value = (tooltip as { value?: string }).value;
      return typeof value === "string" ? value : undefined;
    }
    return undefined;
  }

  private extractIconId(
    iconPath: vscode.TreeItem["iconPath"],
  ): string | undefined {
    if (!iconPath) {
      return undefined;
    }
    if (iconPath instanceof vscode.ThemeIcon) {
      return iconPath.id;
    }
    if (typeof iconPath === "object" && "id" in iconPath) {
      const possible = iconPath as { id?: string };
      if (typeof possible.id === "string") {
        return possible.id;
      }
    }
    return undefined;
  }

  private extractIconFromCommand(icon: unknown): string | undefined {
    if (typeof icon === "string") {
      const match = icon.match(/^\$\((.+)\)$/);
      if (match) {
        return match[1];
      }
      return icon;
    }
    if (icon && typeof icon === "object" && "id" in icon) {
      const candidate = icon as { id?: string };
      if (typeof candidate.id === "string") {
        return candidate.id;
      }
    }
    return undefined;
  }
}
