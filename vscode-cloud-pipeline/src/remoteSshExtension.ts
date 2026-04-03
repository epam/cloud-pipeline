import * as vscode from 'vscode';

/** Microsoft Remote - SSH (VS Code and compatible hosts). */
export const REMOTE_SSH_VSCODE_ID = 'ms-vscode-remote.remote-ssh';

/** Cursor’s Remote - SSH (Anysphere). */
export const REMOTE_SSH_CURSOR_ID = 'anysphere.remote-ssh';

export function isCursorLikeHost(): boolean {
  const app = (vscode.env.appName ?? '').toLowerCase();
  const scheme = (vscode.env.uriScheme ?? '').toLowerCase();
  return app.includes('cursor') || scheme === 'cursor';
}

/** Extension id to install when the user chooses “Install” (host-specific). */
export function primaryRemoteSshExtensionId(): string {
  return isCursorLikeHost() ? REMOTE_SSH_CURSOR_ID : REMOTE_SSH_VSCODE_ID;
}

/** Ids to accept as “Remote - SSH is available” (any match is enough). */
export function remoteSshExtensionIdsToDetect(): string[] {
  if (isCursorLikeHost()) {
    return [REMOTE_SSH_CURSOR_ID, REMOTE_SSH_VSCODE_ID];
  }
  return [REMOTE_SSH_VSCODE_ID];
}

function hasRemoteSshExtensionInstalled(): boolean {
  for (const id of remoteSshExtensionIdsToDetect()) {
    if (vscode.extensions.getExtension(id)) {
      return true;
    }
  }
  return false;
}

function marketplaceWebUrl(extensionId: string): string {
  return `https://marketplace.visualstudio.com/items?itemName=${encodeURIComponent(extensionId)}`;
}

/**
 * Ensures a Remote - SSH implementation is present before using `vscode-remote://ssh-remote+…`.
 * @returns true if OK to proceed, false if the user cancelled or install was only triggered.
 */
export async function ensureRemoteSshForConnect(brand: string): Promise<boolean> {
  if (hasRemoteSshExtensionInstalled()) {
    return true;
  }

  const installId = primaryRemoteSshExtensionId();
  const product = isCursorLikeHost() ? 'Cursor' : 'Visual Studio Code';
  const pick = await vscode.window.showErrorMessage(
    `${brand}: Install the Remote - SSH extension (${product}) to open this run in a remote window.`,
    { modal: false },
    'Install Remote - SSH',
    'Open in Extensions',
    'Open marketplace in browser'
  );

  if (pick === 'Install Remote - SSH') {
    try {
      await vscode.commands.executeCommand('workbench.extensions.installExtension', installId);
    } catch {
      await vscode.commands.executeCommand('workbench.extensions.search', `@id:${installId}`);
    }
    vscode.window.showInformationMessage(
      `${brand}: After Remote - SSH finishes installing, reload the window and run Connect again.`
    );
    return false;
  }

  if (pick === 'Open in Extensions') {
    await vscode.commands.executeCommand('workbench.extensions.search', `@id:${installId}`);
    return false;
  }

  if (pick === 'Open marketplace in browser') {
    await vscode.env.openExternal(vscode.Uri.parse(marketplaceWebUrl(installId)));
    return false;
  }

  return false;
}
