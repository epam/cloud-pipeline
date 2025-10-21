import * as vscode from "vscode";

export type QuickPickActionItem<TResult> = vscode.QuickPickItem & {
  action: () => TResult;
};

export async function quickPickWithCountdown<
  TItem extends vscode.QuickPickItem,
>(title: string, choices: TItem[], timeoutMs: number): Promise<TItem> {
  const defaultChoice = choices[0];

  const quickPick = vscode.window.createQuickPick();
  quickPick.items = choices;
  quickPick.ignoreFocusOut = true;

  const step = 1000;
  let remaining = Math.ceil(timeoutMs / 1000);

  const updateTitle = () => {
    quickPick.title = title;
    quickPick.placeholder = `auto-select "${defaultChoice.label}" in ${remaining}s`;
  };

  updateTitle();

  const result = new Promise<TItem>((resolve) => {
    quickPick.onDidAccept(() => {
      const selection = quickPick.selectedItems[0] as TItem;
      resolve(selection ? selection : defaultChoice);
      quickPick.hide();
    });

    quickPick.onDidHide(() => {
      resolve(defaultChoice);
    });

    quickPick.show();

    const interval = setInterval(() => {
      remaining--;
      if (remaining == 0) {
        clearInterval(interval);
        quickPick.hide();
      } else {
        updateTitle();
      }
    }, step);
  });

  return result;
}
