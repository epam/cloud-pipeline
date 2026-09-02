interface VsCodeApi {
  postMessage(message: unknown): void;
}

declare function acquireVsCodeApi(): VsCodeApi;

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

type SetDataMessage = {
  command: "setData";
  data: TreeNode[];
};

type IncomingMessage = SetDataMessage;

const vscode = acquireVsCodeApi();
const filterInput = document.getElementById(
  "filter",
) as HTMLInputElement | null;
const filterWrapper = document.querySelector(
  ".filter-wrapper",
) as HTMLDivElement | null;
const filterClearButton = document.getElementById(
  "filter-clear",
) as HTMLButtonElement | null;
const treeContainer = document.getElementById("tree") as HTMLDivElement | null;
let activeContextMenuNode: TreeNode | null = null;
let activeContextMenu: HTMLDivElement | null = null;

if (filterInput) {
  const emitFilterChange = () => {
    updateFilterControls();
    vscode.postMessage({ command: "filterChanged", text: filterInput.value });
  };
  filterInput.addEventListener("input", emitFilterChange);
  filterInput.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      if (!filterInput.value) {
        return;
      }
      event.preventDefault();
      filterInput.value = "";
      emitFilterChange();
    }
  });
  updateFilterControls();

  if (filterClearButton) {
    filterClearButton.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      if (!filterInput.value) {
        return;
      }
      filterInput.value = "";
      emitFilterChange();
      filterInput.focus();
    });
  }
}

function updateFilterControls(): void {
  if (!filterInput || !filterWrapper || !filterClearButton) {
    return;
  }
  const hasValue = filterInput.value.length > 0;
  filterWrapper.classList.toggle("has-value", hasValue);
  filterClearButton.disabled = !hasValue;
}

window.addEventListener("message", (event: MessageEvent<IncomingMessage>) => {
  const message = event.data;
  if (!message || typeof message !== "object") {
    return;
  }

  if (message.command === "setData") {
    renderTree(message.data ?? []);
  }
});

vscode.postMessage({ command: "requestData" });

function renderTree(nodes: TreeNode[]): void {
  if (!treeContainer) {
    return;
  }

  hideContextMenu();

  treeContainer.innerHTML = "";
  if (!nodes.length) {
    const empty = document.createElement("div");
    empty.className = "empty-message";
    empty.textContent = "No runs to display.";
    treeContainer.appendChild(empty);
    return;
  }

  for (const node of nodes) {
    treeContainer.appendChild(createTreeNode(node, 0));
  }
}

function createTreeNode(node: TreeNode, depth: number): HTMLElement {
  const hasChildren = node.hasChildren && node.children.length > 0;

  if (hasChildren) {
    const details = document.createElement("details");
    details.className = "tree-node";
    details.open = node.expanded;

    const summary = document.createElement("summary");
    summary.className = "tree-node-row tree-node-summary";
    summary.style.setProperty("--tree-depth", `${depth * 12}px`);
    if (depth === 0) {
      summary.classList.add("is-root");
    }
    summary.setAttribute("role", "treeitem");
    summary.setAttribute("aria-expanded", String(node.expanded));
    summary.tabIndex = 0;
    populateRow(summary, node, true);
    details.appendChild(summary);
    setupContextMenu(summary, node);

    const childrenContainer = document.createElement("div");
    childrenContainer.className = "tree-node-children";
    for (const child of node.children) {
      childrenContainer.appendChild(createTreeNode(child, depth + 1));
    }
    details.appendChild(childrenContainer);

    details.addEventListener("toggle", () => {
      summary.setAttribute("aria-expanded", String(details.open));
    });

    return details;
  }

  const row = document.createElement("div");
  row.className = "tree-node tree-node-leaf tree-node-row";
  row.style.setProperty("--tree-depth", `${depth * 12}px`);
  if (depth === 0) {
    row.classList.add("is-root");
  }
  row.setAttribute("role", "treeitem");
  row.setAttribute("aria-expanded", "false");
  row.tabIndex = 0;
  populateRow(row, node, false);
  setupContextMenu(row, node);
  return row;
}

function populateRow(
  container: HTMLElement,
  node: TreeNode,
  includeTwistie: boolean,
): void {
  const twistie = document.createElement("span");
  twistie.className = "tree-node-twistie";
  if (!includeTwistie) {
    twistie.classList.add("is-leaf");
  }
  container.appendChild(twistie);

  const icon = document.createElement("span");
  icon.className = "tree-node-icon";
  applyIcon(icon, node.iconId, { emptyClass: "is-empty" });
  container.appendChild(icon);

  const label = document.createElement("span");
  label.className = "tree-node-label";
  label.textContent = node.label;
  container.appendChild(label);

  if (node.description) {
    const description = document.createElement("span");
    description.className = "tree-node-description";
    description.textContent = node.description;
    container.appendChild(description);
  }

  container.title = node.tooltip ?? node.label;

  if (node.actions && node.actions.length) {
    const actionsContainer = document.createElement("div");
    actionsContainer.className = "tree-node-actions";
    for (const action of node.actions) {
      actionsContainer.appendChild(createActionButton(node, action));
    }
    container.appendChild(actionsContainer);
  }
}

function createActionButton(
  node: TreeNode,
  action: TreeNodeAction,
): HTMLButtonElement {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "tree-node-action";
  button.title = action.title ?? action.command;
  button.setAttribute("aria-label", action.title ?? action.command);

  const icon = document.createElement("span");
  icon.className = "tree-node-action-icon";
  applyIcon(icon, action.iconId, { fallback: "•" });
  button.appendChild(icon);

  button.addEventListener("click", (event) => {
    event.preventDefault();
    event.stopPropagation();
    vscode.postMessage({
      command: "executeNodeCommand",
      nodeId: node.nodeId,
      action: action.command,
    });
  });

  button.addEventListener("contextmenu", (event) => {
    event.preventDefault();
    event.stopPropagation();
  });

  return button;
}

type IconOptions = {
  fallback?: string;
  emptyClass?: string;
};

function applyIcon(
  element: HTMLElement,
  iconId: string | undefined,
  options?: IconOptions,
): void {
  element.textContent = "";
  if (options?.emptyClass) {
    element.classList.remove(options.emptyClass);
  }
  element.classList.remove("codicon");
  for (const cls of Array.from(element.classList)) {
    if (cls.startsWith("codicon-")) {
      element.classList.remove(cls);
    }
  }
  if (iconId && isCodiconName(iconId)) {
    const normalized = iconId.toLowerCase();
    element.classList.add("codicon", `codicon-${normalized}`);
    element.setAttribute("aria-hidden", "true");
    return;
  }

  if (options?.fallback) {
    element.textContent = options.fallback;
    element.setAttribute("aria-hidden", "true");
    return;
  }

  if (options?.emptyClass) {
    element.classList.add(options.emptyClass);
  }
  element.setAttribute("aria-hidden", "true");
}

function isCodiconName(value: string): boolean {
  return /^[a-z0-9-]+$/iu.test(value);
}

function setupContextMenu(element: HTMLElement, node: TreeNode): void {
  element.addEventListener("contextmenu", (event) => {
    if (!node.contextMenu || !node.contextMenu.length) {
      hideContextMenu();
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    showContextMenu(node, node.contextMenu, event.clientX, event.clientY);
  });
}

function showContextMenu(
  node: TreeNode,
  actions: TreeNodeAction[],
  clientX: number,
  clientY: number,
): void {
  const menu = ensureContextMenuElement();
  menu.innerHTML = "";

  for (const action of actions) {
    const item = document.createElement("button");
    item.type = "button";
    item.className = "context-menu-item";
    item.title = action.title ?? action.command;

    const icon = document.createElement("span");
    icon.className = "context-menu-icon";
    applyIcon(icon, action.iconId, { fallback: "•" });
    item.appendChild(icon);

    const label = document.createElement("span");
    label.className = "context-menu-label";
    label.textContent = action.title ?? action.command;
    item.appendChild(label);

    item.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      hideContextMenu();
      vscode.postMessage({
        command: "executeNodeCommand",
        nodeId: node.nodeId,
        action: action.command,
      });
    });

    item.addEventListener("contextmenu", (event) => {
      event.preventDefault();
      event.stopPropagation();
    });

    menu.appendChild(item);
  }

  menu.style.visibility = "hidden";
  menu.style.display = "block";
  document.body.appendChild(menu);

  const { innerWidth, innerHeight } = window;
  const menuRect = menu.getBoundingClientRect();
  const margin = 4;
  let left = clientX;
  let top = clientY;
  if (left + menuRect.width + margin > innerWidth) {
    left = Math.max(margin, innerWidth - menuRect.width - margin);
  }
  if (top + menuRect.height + margin > innerHeight) {
    top = Math.max(margin, innerHeight - menuRect.height - margin);
  }

  menu.style.left = `${left}px`;
  menu.style.top = `${top}px`;
  menu.style.visibility = "visible";

  activeContextMenuNode = node;
  activeContextMenu = menu;
}

function hideContextMenu(): void {
  if (activeContextMenu) {
    activeContextMenu.style.display = "none";
    activeContextMenu.innerHTML = "";
  }
  activeContextMenuNode = null;
}

function ensureContextMenuElement(): HTMLDivElement {
  if (activeContextMenu) {
    return activeContextMenu;
  }
  const menu = document.createElement("div");
  menu.className = "context-menu";
  menu.style.display = "none";
  document.body.appendChild(menu);
  activeContextMenu = menu;
  return menu;
}

window.addEventListener("click", () => hideContextMenu());
window.addEventListener("contextmenu", (event) => {
  if (!activeContextMenuNode) {
    return;
  }
  const target = event.target as HTMLElement | null;
  if (!target) {
    hideContextMenu();
    return;
  }
  if (activeContextMenu && activeContextMenu.contains(target)) {
    return;
  }
  hideContextMenu();
});

window.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    hideContextMenu();
  }
});

if (treeContainer) {
  treeContainer.addEventListener("scroll", () => hideContextMenu());
}
