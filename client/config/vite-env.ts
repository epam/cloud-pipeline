import fs from 'fs';
import path from 'path';
import dotenv from 'dotenv';
import {expand as dotenvExpand} from 'dotenv-expand';
import type {ProxyOptions} from 'vite';

const REACT_APP = /^REACT_APP_/i;
const PROXY_COOKIE_ENV_PREFIX = 'PROXY_COOKIE_';

export function isDevProxyDisabled(): boolean {
  return (
    process.env.PROXY_DISABLED === '1' ||
    process.env.PROXY_DISABLED === 'true' ||
    process.env.SKIP_DEV_PROXY === '1' ||
    process.env.SKIP_DEV_PROXY === 'true'
  );
}

function originFromEnvUrl(value: string | undefined): string | null {
  if (!value || typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  const withScheme = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const u = new URL(withScheme);
    return `${u.protocol}//${u.host}`;
  } catch {
    return null;
  }
}

function resolveProxyTarget(): string {
  const explicit =
    process.env.PROXY_TARGET && originFromEnvUrl(process.env.PROXY_TARGET);
  if (explicit) {
    return explicit;
  }
  const fromServer = process.env.SERVER && originFromEnvUrl(process.env.SERVER);
  if (fromServer) {
    return fromServer;
  }
  throw new Error(
    'Set PROXY_TARGET (e.g. https://api.example.com) or SERVER in .env so the Vite dev proxy knows where to forward.',
  );
}

function mergeCookieHeader(
  existing: string | number | string[] | undefined,
  extra: string,
): string | undefined {
  const a = (existing && String(existing).trim()) || '';
  const b = (extra && String(extra).trim()) || '';
  if (!b) {
    return a || undefined;
  }
  if (!a) {
    return b;
  }
  return `${a}; ${b}`;
}

function cookieHeaderFromEnvVars(): string {
  const parts: string[] = [];
  for (const key of Object.keys(process.env)) {
    if (!key.startsWith(PROXY_COOKIE_ENV_PREFIX)) {
      continue;
    }
    const cookieName = key.slice(PROXY_COOKIE_ENV_PREFIX.length);
    if (!cookieName) {
      continue;
    }
    const raw = process.env[key];
    if (raw === undefined) {
      continue;
    }
    parts.push(`${cookieName}=${String(raw)}`);
  }
  return parts.length ? parts.join('; ') : '';
}

type UpstreamRequest = {
  getHeader(name: string): string | number | string[] | undefined;
  setHeader(name: string, value: string): void;
};

function applyUpstreamAuth(proxyReq: UpstreamRequest): void {
  const fromEnv = cookieHeaderFromEnvVars();
  if (fromEnv) {
    const merged = mergeCookieHeader(proxyReq.getHeader('cookie'), fromEnv);
    if (merged) {
      proxyReq.setHeader('Cookie', merged);
    }
  }
  const bearer = process.env.PROXY_BEARER_TOKEN;
  if (bearer !== undefined && String(bearer).trim()) {
    proxyReq.setHeader('Authorization', `Bearer ${String(bearer).trim()}`);
  }
}

function loadDotenvFiles(nodeEnv: string): void {
  const dotenvBase = path.resolve(process.cwd(), '.env');
  const dotenvFiles = [
    `${dotenvBase}.${nodeEnv}.local`,
    `${dotenvBase}.${nodeEnv}`,
    nodeEnv !== 'test' && `${dotenvBase}.local`,
    dotenvBase,
  ].filter(Boolean) as string[];

  for (const dotenvFile of dotenvFiles) {
    if (fs.existsSync(dotenvFile)) {
      dotenvExpand(dotenv.config({path: dotenvFile}));
    }
  }
}

function resolveProductionPublicUrl(): string {
  const envPublicUrl = process.env.PUBLIC_URL;
  if (envPublicUrl) {
    return envPublicUrl.replace(/\/$/, '');
  }

  const packageJsonPath = path.resolve(process.cwd(), 'package.json');
  const pkg = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8')) as {
    homepage?: string;
  };
  if (!pkg.homepage) {
    return '';
  }

  try {
    const {pathname} = new URL(pkg.homepage, 'http://dummy');
    return pathname === '/' ? '' : pathname.replace(/\/$/, '');
  } catch {
    return pkg.homepage.replace(/\/$/, '');
  }
}

function getReactAppEnv(): Record<string, string> {
  return Object.keys(process.env)
    .filter((key) => REACT_APP.test(key))
    .reduce<Record<string, string>>((env, key) => {
      const value = process.env[key];
      if (value !== undefined) {
        env[key] = value;
      }
      return env;
    }, {});
}

export function loadClientEnv(mode: string): void {
  const nodeEnv = mode === 'production' ? 'production' : 'development';
  loadDotenvFiles(nodeEnv);
}

export function resolveViteBase(mode: string): string {
  if (mode !== 'production') {
    return '/';
  }

  const publicUrl = process.env.PUBLIC_URL || resolveProductionPublicUrl();
  return publicUrl ? `${publicUrl}/` : '/';
}

/**
 * Vite `define` map — injects build-time constants as plain __VAR__ globals.
 * process.env.NODE_ENV is kept for third-party library compatibility (React, MobX, etc.).
 */
export function buildEnvDefines(mode: string): Record<string, string> {
  loadClientEnv(mode);

  const isProduction = mode === 'production';
  const defines: Record<string, string> = {
    'process.env.NODE_ENV': JSON.stringify(
      isProduction ? 'production' : 'development',
    ),
    'API_PATH': JSON.stringify('/restapi'),
  };

  if (isProduction) {
    const publicUrl = process.env.PUBLIC_URL || resolveProductionPublicUrl();
    defines['PUBLIC_URL'] = JSON.stringify(publicUrl);
    defines['SERVER'] = JSON.stringify(process.env.PUBLIC_URL ?? publicUrl);
    defines['VERSION'] = JSON.stringify(process.env.VERSION ?? '');
    defines['DEVELOPMENT'] = JSON.stringify(false);
  } else {
    defines['PUBLIC_URL'] = JSON.stringify('');
    defines['SERVER'] = JSON.stringify(
      isDevProxyDisabled() && process.env.SERVER
        ? process.env.SERVER
        : '/pipeline',
    );
    defines['VERSION'] = JSON.stringify('DEVELOPMENT');
    defines['DEVELOPMENT'] = JSON.stringify(true);
  }

  return defines;
}

/**
 * Vite dev-server proxy: forwards `/pipeline` to the upstream from SERVER / PROXY_TARGET.
 * Replaces the standalone `scripts/dev-proxy.js` listener used by webpack dev.
 */
export function buildDevProxy(): Record<string, ProxyOptions> | undefined {
  loadClientEnv('development');

  if (isDevProxyDisabled()) {
    return undefined;
  }

  let target: string;
  try {
    target = resolveProxyTarget();
  } catch (error) {
    console.warn('[vite-proxy]', (error as Error).message);
    return undefined;
  }

  const secure = process.env.PROXY_INSECURE !== '1';

  return {
    '/pipeline': {
      target,
      changeOrigin: true,
      secure,
      ws: true,
      xfwd: true,
      configure(proxy) {
        proxy.on('proxyReq', (proxyReq) => {
          applyUpstreamAuth(proxyReq);
        });
        proxy.on('proxyReqWs', (proxyReq) => {
          applyUpstreamAuth(proxyReq);
        });
      },
    },
  };
}
