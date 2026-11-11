import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { jwtDecode } from 'jwt-decode';

import { isPipeAuthInvalidated } from './authState';

export interface PipeFileConfig {
  api?: string;
  access_key?: string;
  tz?: string;
  proxy?: string;
  codec?: string | null;
}

export interface ResolvedAuth {
  apiUrl: string;
  accessKey: string;
  proxyUser: string;
}

const JWT_SKEW_SEC = 60;

export function isJwtAcceptableForUse(token: string): boolean {
  try {
    const decoded = jwtDecode<{ exp?: number; nbf?: number }>(token);
    const now = Math.floor(Date.now() / 1000);
    if (decoded.nbf !== undefined && decoded.nbf > now + JWT_SKEW_SEC) {
      return false;
    }
    if (decoded.exp !== undefined && decoded.exp <= now + JWT_SKEW_SEC) {
      return false;
    }
    return true;
  } catch {
    return false;
  }
}

function expandHome(p: string): string {
  if (p.startsWith('~/') || p === '~') {
    return path.join(os.homedir(), p.slice(1).replace(/^\//, ''));
  }
  return p;
}

export function pipeConfigPath(): string {
  return path.join(os.homedir(), '.pipe', 'config.json');
}

export function readPipeConfigFile(): PipeFileConfig | null {
  const fp = pipeConfigPath();
  try {
    if (!fs.existsSync(fp)) {
      return null;
    }
    const raw = fs.readFileSync(fp, 'utf8');
    return JSON.parse(raw) as PipeFileConfig;
  } catch {
    return null;
  }
}

/**
 * Merge into ~/.pipe/config.json (mode 0o600), same location as `pipe configure`.
 */
export function writePipeConfigMerged(patch: Partial<PipeFileConfig>): void {
  const fp = pipeConfigPath();
  const dir = path.dirname(fp);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  }
  const existing = readPipeConfigFile() ?? {};
  const next: PipeFileConfig = {
    tz: existing.tz ?? 'local',
    proxy: existing.proxy ?? '',
    codec: existing.codec,
    ...existing,
    ...patch,
  };
  const fd = fs.openSync(fp, 'w', 0o600);
  try {
    fs.writeFileSync(fd, JSON.stringify(next, null, 2), { mode: 0o600 });
  } finally {
    fs.closeSync(fd);
  }
  try {
    fs.chmodSync(fp, 0o600);
  } catch {
    /* ignore */
  }
}

/**
 * Resolve credentials from ~/.pipe/config.json only (same as `pipe` CLI home-dir store).
 * Returns null if file missing, keys missing, JWT expired/nbf-invalid, or session invalidated (401).
 */
export function resolveCredentials(): ResolvedAuth | null {
  if (isPipeAuthInvalidated()) {
    return null;
  }

  const file = readPipeConfigFile();
  const apiUrl = file?.api?.trim();
  const accessKey = file?.access_key?.trim();

  if (!apiUrl || !accessKey) {
    return null;
  }

  if (!isJwtAcceptableForUse(accessKey)) {
    return null;
  }

  let proxyUser: string;
  try {
    const decoded = jwtDecode<{ sub?: string }>(accessKey);
    if (!decoded.sub) {
      return null;
    }
    proxyUser = decoded.sub.split('@')[0];
  } catch {
    return null;
  }

  return { apiUrl: apiUrl.replace(/\/$/, ''), accessKey, proxyUser };
}

export function sshConfigDir(): string {
  return expandHome('~/.ssh');
}

export function sshConfigFragmentPath(runId: number): string {
  const dir = path.join(sshConfigDir(), 'config.d');
  return path.join(dir, `cloud-pipeline-${runId}.conf`);
}

export function ensureConfigDIncludes(mainConfigPath: string): void {
  const includeLine = 'Include config.d/*.conf';
  if (!fs.existsSync(mainConfigPath)) {
    fs.mkdirSync(path.dirname(mainConfigPath), { recursive: true });
    fs.writeFileSync(mainConfigPath, `${includeLine}\n`, { mode: 0o600 });
    return;
  }
  const raw = fs.readFileSync(mainConfigPath, 'utf8');
  let lines = raw.split(/\r?\n/);
  lines = lines.filter((l) => l.trim() !== includeLine);
  while (lines.length > 0 && lines[0] === '') {
    lines.shift();
  }
  const rest = lines.join('\n').replace(/\s+$/, '');
  const next = rest.length > 0 ? `${includeLine}\n${rest}\n` : `${includeLine}\n`;
  fs.writeFileSync(mainConfigPath, next, { mode: 0o600 });
}

export function extensionKeysDir(): string {
  const base = path.join(os.homedir(), '.pipe', '.keys');
  if (!fs.existsSync(base)) {
    fs.mkdirSync(base, { recursive: true, mode: 0o700 });
  }
  return base;
}
