import type { RunDetailPayload } from './api';

const DEFAULT_SSH_USER = 'root';

function paramMap(run: RunDetailPayload): Record<string, string> {
  const m: Record<string, string> = {};
  for (const p of run.pipelineRunParameters ?? []) {
    if (p.value !== undefined && p.value !== null) {
      m[p.name] = String(p.value);
    }
  }
  return m;
}

function parseParentId(params: Record<string, string>): number | undefined {
  const v = params['parent-id'];
  if (v === undefined || v === '0') {
    return undefined;
  }
  const n = parseInt(v, 10);
  return Number.isFinite(n) ? n : undefined;
}

export function isSshInitialized(run: RunDetailPayload, initTaskName = 'InitializeEnvironment'): boolean {
  if (run.status !== 'RUNNING' || !run.podIP) {
    return false;
  }
  const ok =
    initTaskName === 'NONE' ||
    (run.tasks ?? []).some((t) => t.name === initTaskName && t.status === 'SUCCESS');
  return ok;
}

export interface SshCredentials {
  username: string;
  password: string;
}

/** When CP_CAP_SHARE_USERS + parent-id, caller must load parent run and pass its sshPassword */
export async function resolveSshCredentialsWithParent(
  run: RunDetailPayload,
  parentRun: RunDetailPayload | null,
  whoamiShortName: string | undefined,
  defaultRootEnabled: boolean
): Promise<SshCredentials> {
  const params = paramMap(run);
  const runOwner = (run.owner ?? '').split('@')[0];
  const mode =
    params['CP_CAP_SSH_MODE'] ||
    (run.platform?.toLowerCase() === 'windows'
      ? 'owner-sshpass'
      : defaultRootEnabled
        ? 'root'
        : 'owner');

  if (mode === 'user') {
    const u = whoamiShortName ?? runOwner;
    return { username: u, password: u };
  }
  if (mode === 'owner') {
    return { username: runOwner, password: runOwner };
  }
  if (mode === 'owner-sshpass') {
    const pass = resolvePasswordFromRunOrParent(run, parentRun, params, runOwner);
    return { username: runOwner, password: pass };
  }
  const pass = resolvePasswordFromRunOrParent(run, parentRun, params, runOwner);
  return { username: DEFAULT_SSH_USER, password: pass };
}

function resolvePasswordFromRunOrParent(
  run: RunDetailPayload,
  parentRun: RunDetailPayload | null,
  params: Record<string, string>,
  runOwner: string
): string {
  const share = (params['CP_CAP_SHARE_USERS'] ?? '').toLowerCase() === 'true';
  const parentId = parseParentId(params);
  if (share && parentId && parentRun?.sshPassword) {
    return parentRun.sshPassword;
  }
  if (!run.sshPassword) {
    throw new Error('Run has no sshPassword in API response');
  }
  return run.sshPassword;
}
