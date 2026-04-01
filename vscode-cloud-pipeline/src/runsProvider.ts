import * as vscode from 'vscode';
import { ApiAuthError, CloudPipelineApi, RunFilterElement } from './api';
import { jwtDecode } from 'jwt-decode';
import { resolveCredentials } from './config';
import { invalidatePipeAuth } from './authState';
import { getActiveTunnel } from './connectService';
import { getBrandName } from './extensionEnv';
import { runListDisplayName } from './runDisplayName';
import {
  canPauseRunDetail,
  canResumeRunDetail,
  isSshBlockedByPauseState,
} from './runPauseResume';
import { isSshInitialized } from './sshResolve';

export type CpTreeItem = RunTreeItem | vscode.TreeItem;

export interface RunTreeFlags {
  sshReady: boolean;
  pauseEligible: boolean;
  resumeEligible: boolean;
  /** Uppercase status from API detail or list row. */
  displayStatus: string;
}

const SSH_DETAIL_CONCURRENCY = 12;

async function mapWithConcurrency<T, R>(
  items: readonly T[],
  concurrency: number,
  fn: (item: T, index: number) => Promise<R>
): Promise<R[]> {
  const results: R[] = new Array(items.length);
  let index = 0;
  const workers = Array.from({ length: Math.min(concurrency, items.length) || 1 }, async () => {
    while (true) {
      const i = index++;
      if (i >= items.length) {
        break;
      }
      results[i] = await fn(items[i], i);
    }
  });
  await Promise.all(workers);
  return results;
}

export class RunTreeItem extends vscode.TreeItem {
  constructor(
    public readonly run: RunFilterElement,
    public readonly ownerFilter: string,
    public readonly flags: RunTreeFlags
  ) {
    const name = runListDisplayName(run.pipelineName, run.dockerImage);
    super(`${run.id} — ${name}`, vscode.TreeItemCollapsibleState.None);
    this.id = `cp-run-${run.id}`;

    const tunnel = getActiveTunnel(run.id);
    const connected = tunnel !== undefined;
    const { sshReady, pauseEligible, resumeEligible, displayStatus } = flags;

    const ctxParts = ['cpRunItem'];
    if (connected) {
      ctxParts.push('tunnelActive');
    }
    if (sshReady) {
      ctxParts.push('cpRunSshReady');
    } else if (displayStatus === 'RUNNING') {
      ctxParts.push('cpRunWarming');
    }
    if (displayStatus === 'PAUSED') {
      ctxParts.push('cpRunPaused');
    }
    if (displayStatus === 'PAUSING' || displayStatus === 'RESUMING') {
      ctxParts.push('cpRunPauseTransition');
    }
    if (pauseEligible) {
      ctxParts.push('cpRunPauseEligible');
    }
    if (resumeEligible) {
      ctxParts.push('cpRunResumeEligible');
    }
    if (displayStatus === 'RUNNING' || displayStatus === 'PAUSED') {
      ctxParts.push('cpRunStoppable');
    }

    if (connected) {
      this.contextValue = ctxParts.join(' ');
      this.iconPath = new vscode.ThemeIcon(
        'link',
        new vscode.ThemeColor('terminal.ansiGreen')
      );
      const owner = run.owner ?? '';
      this.description = `tunnel :${tunnel.localPort}${owner ? ` · ${owner}` : ''}`;
    } else {
      this.contextValue = ctxParts.join(' ');
      if (displayStatus === 'PAUSED') {
        this.iconPath = new vscode.ThemeIcon(
          'debug-pause',
          new vscode.ThemeColor('icon.foreground')
        );
      } else if (displayStatus === 'PAUSING' || displayStatus === 'RESUMING') {
        this.iconPath = new vscode.ThemeIcon('loading~spin');
      } else {
        this.iconPath = sshReady
          ? new vscode.ThemeIcon('vm-running')
          : new vscode.ThemeIcon('loading~spin');
      }
      this.description = run.owner ?? '';
      if (sshReady) {
        this.command = {
          command: 'cloudPipeline.connectRunFromTree',
          title: 'Connect via SSH (Remote)',
          arguments: [run.id],
        };
      }
    }

    const fullTool = run.pipelineName ?? run.dockerImage ?? '—';
    const nodeType =
      run.nodeType?.trim() || run.instance?.nodeType?.trim() || '—';
    let sshStateLabel: string;
    if (connected) {
      sshStateLabel = '';
    } else if (isSshBlockedByPauseState(displayStatus)) {
      sshStateLabel = '**SSH:** Not available (run is paused or changing state)\n\n';
    } else {
      sshStateLabel = `**SSH:** ${sshReady ? 'Ready to connect' : 'Starting...'}\n\n`;
    }
    const sshLine = sshStateLabel;
    let tipText = `**Run** ${run.id}\n\n**Tool:** ${fullTool}\n\n**Node type:** ${nodeType}\n\n${sshLine}**Owner:** ${run.owner ?? '—'}\n\n**Status:** ${displayStatus}`;
    if (connected && tunnel) {
      tipText += `\n\n**SSH tunnel:** \`127.0.0.1:${tunnel.localPort}\` → run`;
    }
    this.tooltip = new vscode.MarkdownString(tipText);
  }
}

export class CloudPipelineRunsProvider implements vscode.TreeDataProvider<CpTreeItem> {
  private _onDidChange = new vscode.EventEmitter<CpTreeItem | undefined | void>();
  readonly onDidChangeTreeData = this._onDidChange.event;

  constructor(private readonly onAfterTreeResolved?: () => void) {}

  refresh(): void {
    this._onDidChange.fire();
  }

  getTreeItem(element: CpTreeItem): vscode.TreeItem {
    return element;
  }

  getParent(): vscode.ProviderResult<CpTreeItem> {
    return undefined;
  }

  async getChildren(element?: CpTreeItem): Promise<CpTreeItem[]> {
    if (element !== undefined) {
      return [];
    }
    try {
      const auth = resolveCredentials();
      if (!auth) {
        const it = new vscode.TreeItem('Sign in', vscode.TreeItemCollapsibleState.None);
        it.id = 'cp-action-signin';
        it.contextValue = 'cpSignIn';
        it.iconPath = new vscode.ThemeIcon('account');
        it.command = { command: 'cloudPipeline.signIn', title: 'Sign in' };
        it.tooltip = `${getBrandName()}: SSO login via web-browser`;
        return [it];
      }

      let owner = auth.proxyUser;
      try {
        const decoded = jwtDecode<{ sub?: string }>(auth.accessKey);
        if (decoded.sub) {
          owner = decoded.sub;
        }
      } catch {
        /* use proxyUser */
      }

      try {
        const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
        const filter = await api.listRunningRunsForOwner(owner, 200);
        if (!filter.elements?.length) {
          const emptyIt = new vscode.TreeItem(
            'No active runs for your user (running / paused)',
            vscode.TreeItemCollapsibleState.None
          );
          emptyIt.id = 'cp-empty-runs';
          emptyIt.iconPath = new vscode.ThemeIcon('info');
          return [emptyIt];
        }
        const elements = filter.elements;
        const initTask = process.env.CP_SSH_INIT_TASK_NAME ?? 'InitializeEnvironment';

        let diskPrefJson: string | undefined;
        let diskPrefOk = false;
        try {
          diskPrefJson = await api.getPreference('launch.job.disk.size.thresholds');
          diskPrefOk = true;
        } catch {
          diskPrefJson = undefined;
          diskPrefOk = false;
        }

        const details = await mapWithConcurrency(elements, SSH_DETAIL_CONCURRENCY, async (r) => {
          try {
            return await api.getRunWithTasks(r.id);
          } catch {
            return null;
          }
        });
        return elements.map((r, i) => {
          const d = details[i];
          const displayStatus = (d?.status ?? r.status ?? '').toUpperCase() || 'RUNNING';
          const sshReady =
            d !== null &&
            !isSshBlockedByPauseState(displayStatus) &&
            isSshInitialized(d, initTask);
          const pauseEligible = d !== null && canPauseRunDetail(d, diskPrefJson, diskPrefOk);
          const resumeEligible = d !== null && canResumeRunDetail(d);
          const flags: RunTreeFlags = {
            sshReady,
            pauseEligible,
            resumeEligible,
            displayStatus,
          };
          return new RunTreeItem(r, owner, flags);
        });
      } catch (e) {
        if (e instanceof ApiAuthError) {
          invalidatePipeAuth();
          this._onDidChange.fire();
          const it = new vscode.TreeItem('Session expired — Sign in again', vscode.TreeItemCollapsibleState.None);
          it.id = 'cp-action-signin';
          it.contextValue = 'cpSignIn';
          it.iconPath = new vscode.ThemeIcon('key');
          it.command = { command: 'cloudPipeline.signIn', title: 'Sign in' };
          it.tooltip = `${getBrandName()}: sign in again (browser login)`;
          return [it];
        }
        const msg = e instanceof Error ? e.message : String(e);
        const errIt = new vscode.TreeItem(`Error: ${msg}`, vscode.TreeItemCollapsibleState.None);
        errIt.id = 'cp-error';
        errIt.iconPath = new vscode.ThemeIcon('error');
        return [errIt];
      }
    } finally {
      this.onAfterTreeResolved?.();
    }
  }
}
