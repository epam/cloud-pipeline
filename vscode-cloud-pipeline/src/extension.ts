import * as vscode from 'vscode';
import { jwtDecode } from 'jwt-decode';
import { ApiAuthError, CloudPipelineApi } from './api';
import { clearPipeAuthInvalidation, invalidatePipeAuth } from './authState';
import { resolveCredentials, writePipeConfigMerged } from './config';
import {
  connectToRun,
  getActiveTunnel,
  listActiveTunnelRunIds,
  stopAllTunnels,
  stopTunnelForRun,
} from './connectService';
import { runBrowserLogin } from './pipePkceLogin';
import {
  getBrandName,
  getDefaultApiBase,
  getTreeViewTitle,
  loadExtensionEnv,
} from './extensionEnv';
import { runListDisplayName } from './runDisplayName';
import { CloudPipelineRunsProvider, RunTreeItem } from './runsProvider';
import { syncMcpFromWorkspaceSettings } from './mcpCursorConfig';
import { runStartNewRunFlow } from './startNewRun';

function normalizeApiBase(input: string): string {
  return input.trim().replace(/\/$/, '');
}

function runIdFromConnectArg(arg: unknown): number | undefined {
  if (typeof arg === 'number' && Number.isFinite(arg)) {
    return arg;
  }
  if (arg && typeof arg === 'object' && 'run' in arg) {
    const id = (arg as { run?: { id?: number } }).run?.id;
    if (typeof id === 'number' && Number.isFinite(id)) {
      return id;
    }
  }
  return undefined;
}

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  loadExtensionEnv(context.extensionPath);

  const brand = getBrandName();

  const signInStatus = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
  signInStatus.command = 'cloudPipeline.signIn';
  signInStatus.text = '$(key) Sign in';
  signInStatus.tooltip = `${brand}: Sign in (using SSO via web-browser)`;

  function updateSignInStatusBar(): void {
    if (resolveCredentials()) {
      signInStatus.hide();
    } else {
      signInStatus.show();
    }
  }

  const provider = new CloudPipelineRunsProvider(() => updateSignInStatusBar());
  context.subscriptions.push(signInStatus);

  const treeView = vscode.window.createTreeView('cloudPipelineRuns', { treeDataProvider: provider });
  treeView.title = getTreeViewTitle();
  context.subscriptions.push(treeView);

  const AUTO_REFRESH_MS = 5000;
  const autoRefreshTimer = setInterval(() => {
    void provider.refreshIfChanged();
  }, AUTO_REFRESH_MS);
  context.subscriptions.push({
    dispose: () => clearInterval(autoRefreshTimer),
  });

  updateSignInStatusBar();

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.refreshRuns', () => {
      clearPipeAuthInvalidation();
      provider.refresh();
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.startNewRun', async () => {
      const auth = resolveCredentials();
      if (!auth) {
        const pick = await vscode.window.showErrorMessage(
          `Not signed in. Use ${brand}: Sign in or ~/.pipe/config.json with api and access_key.`,
          'Sign in',
          'Open Settings'
        );
        if (pick === 'Sign in') {
          await vscode.commands.executeCommand('cloudPipeline.signIn');
        } else if (pick === 'Open Settings') {
          await vscode.commands.executeCommand('cloudPipeline.openSettings');
        }
        return;
      }
      try {
        const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
        await runStartNewRunFlow(api, brand, () => {
          clearPipeAuthInvalidation();
          provider.refresh();
        });
      } catch (e) {
        if (e instanceof ApiAuthError) {
          invalidatePipeAuth();
          provider.refresh();
          updateSignInStatusBar();
          vscode.window.showErrorMessage(
            `${brand} session expired or forbidden. Use Sign in or refresh after fixing ~/.pipe/config.json.`
          );
          return;
        }
        const msg = e instanceof Error ? e.message : String(e);
        vscode.window.showErrorMessage(`${brand}: ${msg}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.openSettings', async () => {
      await vscode.commands.executeCommand(
        'workbench.action.openSettings',
        'cloudPipeline'
      );
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.syncMcpCursorConfig', () => {
      const auth = resolveCredentials();
      if (!auth) {
        void vscode.window.showErrorMessage(
          `Not signed in. Use ${brand}: Sign in or ~/.pipe/config.json with api and access_key.`
        );
        return;
      }
      if (!syncMcpFromWorkspaceSettings(auth, context.extensionPath)) {
        void vscode.window.showErrorMessage(
          `${brand}: Could not derive MCP URL from API URL "${auth.apiUrl}". Set cloudPipeline.mcp.serverUrl explicitly and try again.`
        );
        return;
      }
      void vscode.window.showInformationMessage(
        `${brand}: Updated ~/.cursor/mcp.json for remote MCP. Restart Cursor if the MCP server list does not refresh.`
      );
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.signIn', async () => {
      const fromEnv = getDefaultApiBase();
      let apiBase: string;
      if (fromEnv) {
        apiBase = normalizeApiBase(fromEnv);
      } else {
        const apiInput = await vscode.window.showInputBox({
          title: `${brand} API base URL`,
          prompt: 'Same base URL as in ~/.pipe/config.json (e.g. https://host/pipeline/restapi)',
          ignoreFocusOut: true,
          validateInput: (v) => {
            const t = v?.trim() ?? '';
            if (!t) {
              return 'Enter a non-empty API URL';
            }
            return undefined;
          },
        });
        if (apiInput === undefined) {
          return;
        }
        apiBase = normalizeApiBase(apiInput);
      }
      try {
        const token = await vscode.window.withProgress(
          {
            location: vscode.ProgressLocation.Notification,
            title: `${brand}: complete sign-in in your browser…`,
            cancellable: true,
          },
          async (progress, cancelToken) => {
            progress.report({ message: 'Waiting for authorization…' });
            return runBrowserLogin(apiBase, (u) => vscode.env.openExternal(u), cancelToken);
          }
        );
        writePipeConfigMerged({
          api: apiBase,
          access_key: token,
        });
        clearPipeAuthInvalidation();
        provider.refresh();
        updateSignInStatusBar();
        const mcpCfg = vscode.workspace.getConfiguration('cloudPipeline');
        let mcpExtra = '';
        if (mcpCfg.get<boolean>('mcp.syncOnSignIn', true)) {
          if (syncMcpFromWorkspaceSettings({ apiUrl: apiBase, accessKey: token }, context.extensionPath)) {
            mcpExtra =
              ' Cursor MCP config updated (~/.cursor/mcp.json). Restart Cursor if the MCP server list does not refresh.';
          }
        }
        vscode.window.showInformationMessage(
          `Signed in. Configuration saved to ~/.pipe/config.json.${mcpExtra}`
        );
      } catch (e) {
        if (e instanceof vscode.CancellationError) {
          return;
        }
        const msg = e instanceof Error ? e.message : String(e);
        vscode.window.showErrorMessage(`${brand} sign-in failed: ${msg}`);
      }
    })
  );

  let treeOpenConnect:
    | { runId: number; at: number; clearTimer: ReturnType<typeof setTimeout> }
    | undefined;

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.connectRunFromTree', async (runId: unknown) => {
      if (typeof runId !== 'number' || !Number.isFinite(runId) || getActiveTunnel(runId)) {
        return;
      }

      const openMode = vscode.workspace.getConfiguration('workbench').get<string>('list.openMode');
      if (openMode === 'doubleClick') {
        await vscode.commands.executeCommand('cloudPipeline.connectRun', runId);
        return;
      }

      const now = Date.now();
      const isSecondOpen =
        treeOpenConnect !== undefined &&
        treeOpenConnect.runId === runId &&
        now - treeOpenConnect.at < 450;

      if (treeOpenConnect?.clearTimer) {
        clearTimeout(treeOpenConnect.clearTimer);
      }

      if (isSecondOpen) {
        treeOpenConnect = undefined;
        await vscode.commands.executeCommand('cloudPipeline.connectRun', runId);
        return;
      }

      treeOpenConnect = {
        runId,
        at: now,
        clearTimer: setTimeout(() => {
          treeOpenConnect = undefined;
        }, 450),
      };
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand(
      'cloudPipeline.connectRun',
      async (arg?: unknown) => {
      const auth = resolveCredentials();
      if (!auth) {
        const pick = await vscode.window.showErrorMessage(
          `Not signed in. Use ${brand}: Sign in or ~/.pipe/config.json with api and access_key.`,
          'Sign in',
          'Open Settings'
        );
        if (pick === 'Sign in') {
          await vscode.commands.executeCommand('cloudPipeline.signIn');
        } else if (pick === 'Open Settings') {
          await vscode.commands.executeCommand('cloudPipeline.openSettings');
        }
        return;
      }

      let runId: number | undefined = runIdFromConnectArg(arg);

      if (runId !== undefined && getActiveTunnel(runId)) {
        return;
      }

      if (runId === undefined) {
        let owner = auth.proxyUser;
        try {
          const d = jwtDecode<{ sub?: string }>(auth.accessKey);
          if (d.sub) {
            owner = d.sub;
          }
        } catch {
          /* keep proxyUser */
        }
        try {
          const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
          const filter = await api.listRunningRunsForOwner(owner, 100);
          const picks = (filter.elements ?? [])
            .filter((r) => (r.status || '').toUpperCase() === 'RUNNING')
            .map((r) => ({
              label: `${r.id} — ${runListDisplayName(r.pipelineName, r.dockerImage)}`,
              description: r.owner,
              id: r.id,
            }));
          if (!picks.length) {
            vscode.window.showInformationMessage(
              'No RUNNING runs available for SSH (paused runs must be resumed first).'
            );
            return;
          }
          const chosen = await vscode.window.showQuickPick(picks, {
            placeHolder: 'Select a run',
          });
          runId = chosen?.id;
        } catch (e) {
          if (e instanceof ApiAuthError) {
            invalidatePipeAuth();
            provider.refresh();
            updateSignInStatusBar();
            vscode.window.showErrorMessage(
              `${brand} session expired or forbidden. Use Sign in or refresh after fixing ~/.pipe/config.json.`
            );
            return;
          }
          const msg = e instanceof Error ? e.message : String(e);
          vscode.window.showErrorMessage(`Failed to list runs: ${msg}`);
          return;
        }
      }

      if (runId === undefined) {
        return;
      }

      if (getActiveTunnel(runId)) {
        return;
      }

      try {
        await connectToRun(auth, runId);
      } catch (e) {
        if (e instanceof ApiAuthError) {
          invalidatePipeAuth();
          provider.refresh();
          updateSignInStatusBar();
          vscode.window.showErrorMessage(
            `${brand} session expired or forbidden. Use Sign in or refresh after fixing ~/.pipe/config.json.`
          );
          return;
        }
        const msg = e instanceof Error ? e.message : String(e);
        vscode.window.showErrorMessage(`Connect failed: ${msg}`);
      } finally {
        provider.refresh();
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.terminatePausedRun', async (item?: RunTreeItem) => {
      await vscode.commands.executeCommand('cloudPipeline.stopRun', item);
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.stopRun', async (item?: RunTreeItem) => {
      if (!item?.run?.id) {
        vscode.window.showInformationMessage(
          `${brand}: Right‑click a run in the tree and choose Stop run.`
        );
        return;
      }
      const runId = item.run.id;
      const name = runListDisplayName(item.run.pipelineName, item.run.dockerImage);
      const isPaused = item.flags.displayStatus === 'PAUSED';
      const confirm = await vscode.window.showWarningMessage(
        isPaused ? `Terminate paused run ${runId} — ${name}?` : `Stop run ${runId} — ${name}?`,
        {
          modal: true,
          detail: isPaused
            ? `This drops the cloud instance and ends the paused run in ${brand} (same as the web UI Terminate action).`
            : `This stops the run in ${brand}.`,
        },
        isPaused ? 'Terminate' : 'Stop run'
      );
      if (confirm !== (isPaused ? 'Terminate' : 'Stop run')) {
        return;
      }

      const auth = resolveCredentials();
      if (!auth) {
        vscode.window.showErrorMessage(`Not signed in. Use ${brand}: Sign in first.`);
        return;
      }

      try {
        const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
        if (isPaused) {
          await api.terminateRun(runId);
        } else {
          await api.stopRun(runId);
        }
        if (getActiveTunnel(runId)) {
          await stopTunnelForRun(runId);
        }
        vscode.window.showInformationMessage(
          `${brand}: Run ${runId} ${isPaused ? 'terminated' : 'stopped'}.`
        );
        clearPipeAuthInvalidation();
        provider.refresh();
        updateSignInStatusBar();
      } catch (e) {
        if (e instanceof ApiAuthError) {
          invalidatePipeAuth();
          provider.refresh();
          updateSignInStatusBar();
          vscode.window.showErrorMessage(
            `${brand} session expired or forbidden. Use Sign in or refresh after fixing ~/.pipe/config.json.`
          );
          return;
        }
        const msg = e instanceof Error ? e.message : String(e);
        vscode.window.showErrorMessage(`${brand}: Failed to stop run: ${msg}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.pauseRun', async (item?: RunTreeItem) => {
      if (!item?.run?.id) {
        vscode.window.showInformationMessage(
          `${brand}: Right‑click a run in the tree and choose Pause run.`
        );
        return;
      }
      const auth = resolveCredentials();
      if (!auth) {
        vscode.window.showErrorMessage(`Not signed in. Use ${brand}: Sign in first.`);
        return;
      }
      try {
        const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
        await api.pauseRun(item.run.id);
        vscode.window.showInformationMessage(`${brand}: Pause requested for run ${item.run.id}.`);
        clearPipeAuthInvalidation();
        provider.refresh();
        updateSignInStatusBar();
      } catch (e) {
        if (e instanceof ApiAuthError) {
          invalidatePipeAuth();
          provider.refresh();
          updateSignInStatusBar();
          vscode.window.showErrorMessage(
            `${brand} session expired or forbidden. Use Sign in or refresh after fixing ~/.pipe/config.json.`
          );
          return;
        }
        const msg = e instanceof Error ? e.message : String(e);
        vscode.window.showErrorMessage(`${brand}: Failed to pause run: ${msg}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.resumeRun', async (item?: RunTreeItem) => {
      if (!item?.run?.id) {
        vscode.window.showInformationMessage(
          `${brand}: Right‑click a run in the tree and choose Resume run.`
        );
        return;
      }
      const auth = resolveCredentials();
      if (!auth) {
        vscode.window.showErrorMessage(`Not signed in. Use ${brand}: Sign in first.`);
        return;
      }
      try {
        const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
        await api.resumeRun(item.run.id);
        vscode.window.showInformationMessage(`${brand}: Resume requested for run ${item.run.id}.`);
        clearPipeAuthInvalidation();
        provider.refresh();
        updateSignInStatusBar();
      } catch (e) {
        if (e instanceof ApiAuthError) {
          invalidatePipeAuth();
          provider.refresh();
          updateSignInStatusBar();
          vscode.window.showErrorMessage(
            `${brand} session expired or forbidden. Use Sign in or refresh after fixing ~/.pipe/config.json.`
          );
          return;
        }
        const msg = e instanceof Error ? e.message : String(e);
        vscode.window.showErrorMessage(`${brand}: Failed to resume run: ${msg}`);
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('cloudPipeline.stopTunnel', async (item?: RunTreeItem) => {
      let chosen: number | undefined = item?.run.id;
      if (chosen === undefined) {
        const ids = listActiveTunnelRunIds();
        if (!ids.length) {
          vscode.window.showInformationMessage(`No active ${brand} SSH tunnels in this window.`);
          return;
        }
        if (ids.length === 1) {
          chosen = ids[0];
        } else {
          const pick = await vscode.window.showQuickPick(
            ids.map((id) => ({ label: `Run ${id}`, id })),
            { placeHolder: 'Stop tunnel for run' }
          );
          chosen = pick?.id;
        }
      }
      if (chosen !== undefined) {
        await stopTunnelForRun(chosen);
        vscode.window.showInformationMessage(`Tunnel for run ${chosen} stopped.`);
        provider.refresh();
      }
    })
  );
}

export function deactivate(): Thenable<void> {
  return stopAllTunnels();
}
