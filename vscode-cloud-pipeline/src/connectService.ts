import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { CloudPipelineApi } from './api';
import {
  ensureConfigDIncludes,
  extensionKeysDir,
  ResolvedAuth,
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
import { parseEdgeUrl, startLocalTunnelServer, TunnelTarget } from './tunnel';

export interface ActiveTunnel {
  runId: number;
  localPort: number;
  close: () => Promise<void>;
}

const tunnels = new Map<number, ActiveTunnel>();

/**
 * Remote - SSH folder URI built with {@link vscode.Uri.from} so `ssh-remote+<host>` is not
 * misparsed by {@link vscode.Uri.parse}. Opening only `/` as the folder can break the integrated
 * terminal (UriError: path cannot begin with "//" when authority is missing).
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
  return tunnels.get(runId);
}

export function listActiveTunnelRunIds(): number[] {
  return [...tunnels.keys()];
}

export async function stopTunnelForRun(runId: number): Promise<void> {
  const t = tunnels.get(runId);
  if (t) {
    await t.close();
    tunnels.delete(runId);
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
  await Promise.all([...tunnels.keys()].map((id) => stopTunnelForRun(id)));
}

function resolveAuthorizedUsersForKey(
  runOwnerShort: string,
  whoamiShort: string | undefined,
  credUser: string
): string[] {
  const set = new Set<string>([credUser, runOwnerShort, whoamiShort].filter(Boolean) as string[]);
  return [...set];
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

  const existing = tunnels.get(runId);
  if (existing) {
    await existing.close();
    tunnels.delete(runId);
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
      }
    : {
        proxyHost: edgeHost,
        proxyPort: edgePort,
        remoteHost: run.podIP,
        remotePort: 22,
        proxyAuthUser: auth.proxyUser,
        proxyAuthPassword: auth.accessKey,
      };

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: `${getBrandName()}: SSH tunnel for run ${runId}`,
      cancellable: false,
    },
    async () => {
      const server = await startLocalTunnelServer(target);
      tunnels.set(runId, {
        runId,
        localPort: server.localPort,
        close: () => server.close(),
      });

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

        // Open remote in this app. `vscode.openFolder` stays in-process (Cursor or VS Code).
        const remoteUri = remoteSshWorkspaceUri(hostAlias, keyResult.sshConfigUser);
        await vscode.commands.executeCommand('vscode.openFolder', remoteUri, true);
        vscode.window.showInformationMessage(
          `Tunnel active on 127.0.0.1:${server.localPort} for run ${runId}. Use "Stop SSH Tunnel" when done.`
        );
      } catch (e) {
        await server.close();
        tunnels.delete(runId);
        const msg = e instanceof Error ? e.message : String(e);
        vscode.window.showErrorMessage(`${getBrandName()} connect failed: ${msg}`);
      }
    }
  );
}
