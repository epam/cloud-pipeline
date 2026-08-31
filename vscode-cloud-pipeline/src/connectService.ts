import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { CloudPipelineApi } from './api';
import {
  ensureConfigDIncludes,
  extensionKeysDir,
  ResolvedAuth,
  resolveCredentials,
  sshConfigDir,
  sshConfigFragmentPath,
} from './config';
import { getBrandName } from './extensionEnv';
import { provisionPasswordlessKey } from './keyProvisioning';
import { ensureRemoteSshForConnect } from './remoteSshExtension';
import { isSshBlockedByPauseState } from './runPauseResume';
import {
  isSshInitialized,
  resolveSshCredentialsWithParent,
} from './sshResolve';
import { invalidatePipeAuth } from './authState';
import { parseEdgeUrl, startLocalTunnelServer, TunnelProxyAuthError, TunnelServerHandle, TunnelTarget } from './tunnel';

export interface ActiveTunnel {
  runId: number;
  localPort: number;
  close: () => Promise<void>;
}

/** Internal record — extends ActiveTunnel with reconnection state. Not exported. */
interface TunnelRecord extends ActiveTunnel {
  target: TunnelTarget;
  serverHandle: TunnelServerHandle;
  healthTimer: ReturnType<typeof setInterval> | undefined;
  /** Prevents flooding the user with repeated auth-error notifications for the same tunnel. */
  authErrorNotified: boolean;
}

const tunnelRecords = new Map<number, TunnelRecord>();

/** Guards against concurrent reconnect attempts for the same run. */
const reconnecting = new Set<number>();

/** Guards against concurrent health-check ticks for the same run. */
const healthChecking = new Set<number>();

const HEALTH_CHECK_INTERVAL_MS = 60_000;

/**
 * Remote-SSH folder URI built with {@link vscode.Uri.from} so `ssh-remote+<host>` is not
 * misparsed by {@link vscode.Uri.parse}.
 */
function remoteSshWorkspaceUri(hostAlias: string, remoteUser: string): vscode.Uri {
  const u = (remoteUser.trim() || 'root').split('@')[0];
  const remotePath = u === 'root' ? '/root' : `/home/${u}`;
  return vscode.Uri.from({
    scheme: 'vscode-remote',
    authority: `ssh-remote+${hostAlias}`,
    path: remotePath,
  });
}

export function getActiveTunnel(runId: number): ActiveTunnel | undefined {
  return tunnelRecords.get(runId);
}

export function listActiveTunnelRunIds(): number[] {
  return [...tunnelRecords.keys()];
}

export async function stopTunnelForRun(runId: number): Promise<void> {
  const rec = tunnelRecords.get(runId);
  if (rec) {
    if (rec.healthTimer !== undefined) {
      clearInterval(rec.healthTimer);
      rec.healthTimer = undefined;
    }
    await rec.close();
    tunnelRecords.delete(runId);
  }
  try {
    fs.unlinkSync(sshConfigFragmentPath(runId));
  } catch {
    /* ignore */
  }
  try {
    fs.unlinkSync(path.join(sshConfigDir(), `known_hosts.cp.${runId}`));
  } catch {
    /* ignore */
  }
}

export async function stopAllTunnels(): Promise<void> {
  await Promise.all([...tunnelRecords.keys()].map((id) => stopTunnelForRun(id)));
}

function resolveAuthorizedUsersForKey(
  runOwnerShort: string,
  whoamiShort: string | undefined,
  credUser: string
): string[] {
  const set = new Set<string>([credUser, runOwnerShort, whoamiShort].filter(Boolean) as string[]);
  return [...set];
}

/**
 * Restarts the local tunnel server for an existing tunnel on the same port.
 * Does not re-provision SSH keys or reopen the VS Code window — the existing
 * SSH config and authorized_keys remain valid.  When the server is back on the
 * same port, VS Code Remote-SSH's "Retry" / reconnect attempts succeed automatically.
 */
export async function reconnectTunnel(runId: number): Promise<void> {
  if (reconnecting.has(runId)) {
    return;
  }
  const rec = tunnelRecords.get(runId);
  if (!rec) {
    return;
  }

  reconnecting.add(runId);
  try {
    // Pause health monitor during restart to avoid re-entrant calls.
    if (rec.healthTimer !== undefined) {
      clearInterval(rec.healthTimer);
      rec.healthTimer = undefined;
    }

    try {
      await rec.serverHandle.close();
    } catch {
      /* ignore close errors */
    }

    const newServer = await startLocalTunnelServer(rec.target, rec.localPort);

    rec.serverHandle = newServer;
    rec.close = () => newServer.close();
    rec.authErrorNotified = false;

    // Resume health monitor.
    rec.healthTimer = setInterval(() => {
      void runHealthCheck(runId);
    }, HEALTH_CHECK_INTERVAL_MS);

    vscode.window.showInformationMessage(
      `${getBrandName()}: SSH tunnel for run ${runId} has been restarted on port ${rec.localPort}. ` +
      `Click "Retry" in the Remote-SSH dialog if prompted.`
    );
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    vscode.window.showErrorMessage(
      `${getBrandName()}: Failed to restart SSH tunnel for run ${runId}: ${msg}`
    );
    // Restart health monitor anyway so we keep checking.
    if (tunnelRecords.has(runId)) {
      rec.healthTimer = setInterval(() => {
        void runHealthCheck(runId);
      }, HEALTH_CHECK_INTERVAL_MS);
    }
  } finally {
    reconnecting.delete(runId);
  }
}

async function runHealthCheck(runId: number): Promise<void> {
  if (healthChecking.has(runId)) {
    return;
  }
  const rec = tunnelRecords.get(runId);
  if (!rec) {
    return;
  }

  healthChecking.add(runId);
  try {
    const auth = resolveCredentials();
    if (!auth) {
      // No valid credentials — skip this tick; the tunnel's getProxyAuth will handle it.
      return;
    }

    let runStatus: string;
    try {
      const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
      const run = await api.getRun(runId);
      runStatus = (run.status ?? '').toUpperCase();
    } catch {
      // Network error or auth issue — skip this tick rather than cleaning up prematurely.
      return;
    }

    // If the run is no longer active, tear down the tunnel.
    const isActive = ['RUNNING', 'PAUSING', 'PAUSED', 'RESUMING'].includes(runStatus);
    if (!isActive) {
      await stopTunnelForRun(runId);
      vscode.window.showInformationMessage(
        `${getBrandName()}: Run ${runId} has ended (status: ${runStatus}). SSH tunnel closed.`
      );
      return;
    }

    // If the net.Server has died but the run is still alive, attempt a silent restart.
    if (!rec.serverHandle.listening) {
      await reconnectTunnel(runId);
    }
  } finally {
    healthChecking.delete(runId);
  }
}

export async function connectToRun(auth: ResolvedAuth, runId: number): Promise<void> {
  if (!(await ensureRemoteSshForConnect(getBrandName()))) {
    return;
  }

  const cfg = vscode.workspace.getConfiguration('cloudPipeline');
  const region = (cfg.get<string>('region') ?? '').trim() || undefined;
  const directTunnel = cfg.get<boolean>('directTunnel') ?? false;
  const hostPrefix = (cfg.get<string>('sshHostAliasPrefix') ?? 'cp-run-').trim() || 'cp-run-';
  const hostAlias = `${hostPrefix}${runId}`;

  const api = new CloudPipelineApi(auth.apiUrl, auth.accessKey);
  const run = await api.getRunWithTasks(runId);

  if (isSshBlockedByPauseState(run.status)) {
    vscode.window.showErrorMessage(
      `${getBrandName()}: SSH is not available while the run is ${(run.status || '').toLowerCase()}.`
    );
    return;
  }

  if (run.sensitive) {
    vscode.window.showErrorMessage(
      `${getBrandName()}: tunnel connections to sensitive runs are not allowed.`
    );
    return;
  }

  if (!isSshInitialized(run)) {
    const ok = await vscode.window.showWarningMessage(
      'This run may not be ready for SSH (init task / pod IP). Connect anyway?',
      'Connect',
      'Cancel'
    );
    if (ok !== 'Connect') {
      return;
    }
  }

  if (!run.podIP) {
    vscode.window.showErrorMessage('Run has no pod IP yet.');
    return;
  }

  const params = Object.fromEntries(
    (run.pipelineRunParameters ?? [])
      .filter((p) => p.value !== undefined && p.value !== null)
      .map((p) => [p.name, String(p.value)])
  );
  let parentRun = null as Awaited<ReturnType<CloudPipelineApi['getRun']>> | null;
  const parentIdStr = params['parent-id'];
  const share = (params['CP_CAP_SHARE_USERS'] ?? '').toLowerCase() === 'true';
  if (share && parentIdStr && parentIdStr !== '0') {
    const pid = parseInt(parentIdStr, 10);
    if (Number.isFinite(pid)) {
      try {
        parentRun = await api.getRun(pid);
      } catch {
        parentRun = null;
      }
    }
  }

  const prefVal = await api.getPreference('system.ssh.default.root.user.enabled');
  const defaultRootEnabled = (prefVal ?? 'true').toLowerCase().trim() === 'true';
  const whoamiShort = (await api.whoamiUserName()) ?? auth.proxyUser;

  const creds = await resolveSshCredentialsWithParent(
    run,
    parentRun,
    whoamiShort,
    defaultRootEnabled
  );

  const existing = tunnelRecords.get(runId);
  if (existing) {
    if (existing.healthTimer !== undefined) {
      clearInterval(existing.healthTimer);
      existing.healthTimer = undefined;
    }
    await existing.close();
    tunnelRecords.delete(runId);
  }

  let edgeHost: string | undefined;
  let edgePort: number | undefined;
  if (!directTunnel) {
    const edgeUrl = await api.getEdgeExternalUrl(region);
    const ep = parseEdgeUrl(edgeUrl);
    edgeHost = ep.host;
    edgePort = ep.port;
  }

  const target: TunnelTarget = directTunnel
    ? {
        directHost: run.podIP,
        directPort: 22,
        remoteHost: run.podIP,
        remotePort: 22,
        proxyAuthUser: auth.proxyUser,
        proxyAuthPassword: auth.accessKey,
        getProxyAuth: () => {
          const fresh = resolveCredentials();
          return fresh ? { user: fresh.proxyUser, password: fresh.accessKey } : null;
        },
      }
    : {
        proxyHost: edgeHost,
        proxyPort: edgePort,
        remoteHost: run.podIP,
        remotePort: 22,
        proxyAuthUser: auth.proxyUser,
        proxyAuthPassword: auth.accessKey,
        getProxyAuth: () => {
          const fresh = resolveCredentials();
          return fresh ? { user: fresh.proxyUser, password: fresh.accessKey } : null;
        },
      };

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: `${getBrandName()}: SSH tunnel for run ${runId}`,
      cancellable: false,
    },
    async () => {
      const server = await startLocalTunnelServer(target);

      // Wire up callbacks now that we have the runId in scope.
      target.onProxyAuthError = (err: TunnelProxyAuthError) => {
        const rec = tunnelRecords.get(runId);
        if (rec && !rec.authErrorNotified) {
          rec.authErrorNotified = true;
          invalidatePipeAuth();
          void vscode.window.showErrorMessage(
            `${getBrandName()}: SSH tunnel for run ${runId} — proxy authentication failed ` +
            `(HTTP ${err.statusCode}). Your session may have expired. Sign in again.`,
            'Sign In'
          ).then((pick) => {
            if (pick === 'Sign In') {
              void vscode.commands.executeCommand('cloudPipeline.signIn');
            }
          });
        }
      };

      target.onServerError = (err: Error) => {
        // Logged only; health monitor will detect server.listening === false and restart.
        console.error(`[${getBrandName()}] Tunnel server error for run ${runId}: ${err.message}`);
      };

      const record: TunnelRecord = {
        runId,
        localPort: server.localPort,
        target,
        serverHandle: server,
        healthTimer: undefined,
        authErrorNotified: false,
        close: () => server.close(),
      };
      tunnelRecords.set(runId, record);

      const runOwnerShort = (run.owner ?? '').split('@')[0];
      const authorizedUsers = resolveAuthorizedUsersForKey(runOwnerShort, whoamiShort, creds.username);

      try {
        const keysDir = extensionKeysDir();
        const keyResult = await provisionPasswordlessKey(
          {
            host: '127.0.0.1',
            port: server.localPort,
            username: creds.username,
            password: creds.password,
            readyTimeout: 60000,
          },
          runId,
          keysDir,
          authorizedUsers,
          creds.username
        );

        const sshDir = sshConfigDir();
        const configD = path.join(sshDir, 'config.d');
        fs.mkdirSync(configD, { recursive: true, mode: 0o700 });

        const knownHostsPath = path.join(sshDir, `known_hosts.cp.${runId}`);
        const khLine = `[127.0.0.1]:${server.localPort} ${keyResult.hostRsaPubKeyBody}\n`;
        fs.writeFileSync(knownHostsPath, khLine, { mode: 0o600 });

        const fragmentPath = sshConfigFragmentPath(runId);
        const fragment = [
          `Host ${hostAlias}`,
          `    HostName 127.0.0.1`,
          `    Port ${server.localPort}`,
          `    User ${keyResult.sshConfigUser}`,
          `    IdentityFile ${keyResult.privateKeyPath}`,
          `    UserKnownHostsFile ${knownHostsPath}`,
          `    StrictHostKeyChecking yes`,
          '',
        ].join('\n');
        fs.writeFileSync(fragmentPath, fragment, { mode: 0o600 });

        const mainConfig = path.join(sshDir, 'config');
        ensureConfigDIncludes(mainConfig);

        // Start background health monitor after the tunnel is fully established.
        record.healthTimer = setInterval(() => {
          void runHealthCheck(runId);
        }, HEALTH_CHECK_INTERVAL_MS);

        // Open remote in this app. `vscode.openFolder` stays in-process (Cursor or VS Code).
        const remoteUri = remoteSshWorkspaceUri(hostAlias, keyResult.sshConfigUser);
        await vscode.commands.executeCommand('vscode.openFolder', remoteUri, true);
        vscode.window.showInformationMessage(
          `Tunnel active on 127.0.0.1:${server.localPort} for run ${runId}. Use "Stop SSH Tunnel" when done.`
        );
      } catch (e) {
        if (record.healthTimer !== undefined) {
          clearInterval(record.healthTimer);
          record.healthTimer = undefined;
        }
        await server.close();
        tunnelRecords.delete(runId);
        const msg = e instanceof Error ? e.message : String(e);
        vscode.window.showErrorMessage(`${getBrandName()} connect failed: ${msg}`);
      }
    }
  );
}
