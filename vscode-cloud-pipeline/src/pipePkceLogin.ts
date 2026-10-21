import * as crypto from 'crypto';
import * as vscode from 'vscode';

import { ApiResponse, httpGetUnauthenticated } from './api';

const DEFAULT_POLL_SEC = 5;
const DEFAULT_ATTEMPTS = 120;

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * Match `pipe-cli` `pkce.generate_code_verifier`: secrets.token_urlsafe(96)[:length].
 */
export function generateCodeVerifier(length = 127): string {
  if (!(43 < length && length < 128)) {
    throw new Error('Invalid code verifier length.');
  }
  return crypto.randomBytes(96).toString('base64url').slice(0, length);
}

/**
 * Match `pipe-cli` `pkce.generate_code_challenge`: urlsafe_b64encode(sha256(verifier))[:-1].
 */
export function generateCodeChallenge(codeVerifier: string): string {
  const sha256Hash = crypto.createHash('sha256').update(codeVerifier, 'ascii').digest();
  const b64 = sha256Hash.toString('base64').replace(/\+/g, '-').replace(/\//g, '_');
  return b64.slice(0, -1);
}

export function generatePkcePair(): { codeVerifier: string; codeChallenge: string } {
  const codeVerifier = generateCodeVerifier();
  const codeChallenge = generateCodeChallenge(codeVerifier);
  return { codeVerifier, codeChallenge };
}

export function buildAuthUrl(apiBase: string, codeChallenge: string): string {
  const base = apiBase.replace(/\/$/, '');
  const u = new URL(`${base}/access/auth`);
  u.searchParams.set('code_challenge', codeChallenge);
  u.searchParams.set('code_challenge_method', 'S256');
  return u.toString();
}

function pollIntervalMs(): number {
  const sec = parseInt(process.env.CP_ACCESS_LOGIN_POOLING_TIMEOUT || String(DEFAULT_POLL_SEC), 10);
  return (Number.isFinite(sec) && sec > 0 ? sec : DEFAULT_POLL_SEC) * 1000;
}

function pollMaxAttempts(): number {
  const n = parseInt(process.env.CP_ACCESS_LOGIN_POOLING_ATTEMPTS || String(DEFAULT_ATTEMPTS), 10);
  return Number.isFinite(n) && n > 0 ? n : DEFAULT_ATTEMPTS;
}

export async function pollAccessCode(
  apiBase: string,
  codeChallenge: string,
  cancel?: vscode.CancellationToken
): Promise<string> {
  const base = apiBase.replace(/\/$/, '');
  const url = new URL(`${base}/access/code`);
  url.searchParams.set('code_challenge', codeChallenge);

  const attempts = pollMaxAttempts();
  const interval = pollIntervalMs();

  for (let i = 0; i < attempts; i++) {
    if (cancel?.isCancellationRequested) {
      throw new vscode.CancellationError();
    }
    const { statusCode, text } = await httpGetUnauthenticated(url.toString());
    if (statusCode === 200) {
      try {
        const data = JSON.parse(text) as ApiResponse<{ code?: string }>;
        if (data.status === 'OK' && data.payload?.code) {
          return data.payload.code;
        }
      } catch {
        /* ignore parse errors, retry */
      }
    }
    await sleep(interval);
  }
  throw new Error('No authorization code received (timeout). Try Sign in again.');
}

export async function exchangeToken(
  apiBase: string,
  codeVerifier: string,
  code: string
): Promise<string> {
  const base = apiBase.replace(/\/$/, '');
  const url = new URL(`${base}/access/token`);
  url.searchParams.set('code_verifier', codeVerifier);
  url.searchParams.set('code', code);

  const { statusCode, text } = await httpGetUnauthenticated(url.toString());
  if (statusCode !== 200) {
    throw new Error(`Token exchange failed: HTTP ${statusCode}`);
  }
  const data = JSON.parse(text) as ApiResponse<{ token?: string }>;
  if (data.status !== 'OK' || !data.payload?.token) {
    throw new Error(data.message || data.status || 'Token exchange failed');
  }
  return data.payload.token;
}

export async function runBrowserLogin(
  apiBase: string,
  openExternal: (u: vscode.Uri) => Thenable<boolean>,
  cancel?: vscode.CancellationToken
): Promise<string> {
  const { codeVerifier, codeChallenge } = generatePkcePair();
  const authUrl = buildAuthUrl(apiBase, codeChallenge);
  await openExternal(vscode.Uri.parse(authUrl));

  const code = await pollAccessCode(apiBase, codeChallenge, cancel);
  return exchangeToken(apiBase, codeVerifier, code);
}
