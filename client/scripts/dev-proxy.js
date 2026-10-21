/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

/**
 * Local reverse proxy for API development: forwards to the real SERVER origin
 * and adds CORS headers so the browser accepts responses from the webpack dev
 * origin (e.g. localhost:3000 vs localhost:8080).
 *
 * Started automatically by `npm start` unless PROXY_DISABLED=true (or SKIP_DEV_PROXY=true).
 * Standalone: `npm run proxy:dev`
 *
 * Env: PROXY_TARGET (origin, overrides SERVER), PROXY_PORT, PROXY_HOST,
 * PROXY_CORS_ORIGIN, PROXY_INSECURE=1 (self-signed upstream TLS).
 * Optional PROXY_SERVER: full URL the dev app uses; webpack defaults to
 * http://localhost:9999/<path-from-SERVER> when unset. This script listens on
 * 127.0.0.1:9999 by default (PROXY_SERVER / PROXY_HOST / PROXY_PORT override).
 * Optional cookies from .env: PROXY_COOKIE_<NAME>=value → adds NAME=value to the
 * Cookie header on every forwarded request (merge with the browser’s Cookie).
 * Example: PROXY_COOKIE_SESSION=abcd → SESSION=abcd
 * Optional PROXY_BEARER_TOKEN — sets Authorization: Bearer <token> on upstream requests.
 * PROXY_SERVER_VERBOSE=true — log each exchange: METHOD proxyURL -> upstreamURL
 * WebSocket: HTTP Upgrade (websocket) is forwarded to the same upstream; cookies
 * and bearer apply to the upgrade request via proxyReqWs.
 */

process.env.BABEL_ENV = process.env.BABEL_ENV || 'development';
process.env.NODE_ENV = process.env.NODE_ENV || 'development';

require('../config/env');

const http = require('http');
const {ServerResponse} = http;
const chalk = require('react-dev-utils/chalk');
const httpProxy = require('http-proxy');

function originFromEnvUrl (value) {
  if (!value || typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  const withScheme = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const u = new URL(withScheme);
    return `${u.protocol}//${u.host}`;
  } catch (e) {
    return null;
  }
}

function resolveTarget () {
  const explicit = process.env.PROXY_TARGET && originFromEnvUrl(process.env.PROXY_TARGET);
  if (explicit) {
    return explicit;
  }
  const fromServer = process.env.SERVER && originFromEnvUrl(process.env.SERVER);
  if (fromServer) {
    return fromServer;
  }
  throw new Error(
    'Set PROXY_TARGET (e.g. https://api.example.com) or SERVER in .env so the proxy knows where to forward.'
  );
}

function buildCorsHeaders (req) {
  const fixed = process.env.PROXY_CORS_ORIGIN;
  const requestOrigin = req.headers.origin;
  const allowOrigin = fixed || requestOrigin || '*';
  const headers = {
    'Access-Control-Allow-Origin': allowOrigin,
    'Access-Control-Allow-Methods':
      'GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS',
    'Access-Control-Allow-Headers':
      req.headers['access-control-request-headers'] ||
      'Content-Type, Authorization, X-Requested-With, Accept',
    'Access-Control-Expose-Headers': '*'
  };
  if (allowOrigin !== '*' && (fixed || requestOrigin)) {
    headers['Access-Control-Allow-Credentials'] = 'true';
  }
  return headers;
}

function mergeCookieHeader (existing, extra) {
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

const PROXY_COOKIE_ENV_PREFIX = 'PROXY_COOKIE_';

function cookieHeaderFromEnvVars () {
  const parts = [];
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
    const value = String(raw);
    parts.push(`${cookieName}=${value}`);
  }
  return parts.length ? parts.join('; ') : '';
}

function cookieNamesFromEnvVars () {
  const names = [];
  for (const key of Object.keys(process.env)) {
    if (!key.startsWith(PROXY_COOKIE_ENV_PREFIX)) {
      continue;
    }
    const cookieName = key.slice(PROXY_COOKIE_ENV_PREFIX.length);
    if (cookieName && process.env[key] !== undefined) {
      names.push(cookieName);
    }
  }
  return names;
}

function parseListenFromProxyServer () {
  const raw = process.env.PROXY_SERVER;
  if (!raw || !String(raw).trim()) {
    return null;
  }
  try {
    const trimmed = raw.trim();
    const href = /^https?:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
    const u = new URL(href);
    const portNum = u.port
      ? parseInt(u.port, 10)
      : (u.protocol === 'https:' ? 443 : 80);
    return {
      host: u.hostname || '127.0.0.1',
      port: portNum
    };
  } catch (e) {
    return null;
  }
}

function isProxyVerbose () {
  const v = process.env.PROXY_SERVER_VERBOSE;
  return v === 'true' || v === '1';
}

function logVerboseProxyExchange (req, host, port, target) {
  if (!isProxyVerbose()) {
    return;
  }
  const method = (req.method || 'GET').toUpperCase();
  const pathAndQuery = req.url || '/';
  const proxyUrl = `http://${host}:${port}${pathAndQuery}`;
  const upstreamBase = String(target).replace(/\/$/, '');
  const forwardUrl = `${upstreamBase}${pathAndQuery}`;
  const wsHint =
    req.headers.upgrade &&
    String(req.headers.upgrade).toLowerCase() === 'websocket'
      ? ' [ws]'
      : '';
  console.log(
    chalk.dim(`[dev-proxy] ${method} ${proxyUrl} -> ${forwardUrl}${wsHint}`)
  );
}

function applyUpstreamAuth (proxyReq, req, host, port, target) {
  logVerboseProxyExchange(req, host, port, target);
  const fromEnv = cookieHeaderFromEnvVars();
  if (fromEnv) {
    const merged = mergeCookieHeader(proxyReq.getHeader('cookie'), fromEnv);
    if (merged) {
      proxyReq.setHeader('Cookie', merged);
    }
  }
  const bearer = process.env.PROXY_BEARER_TOKEN;
  if (bearer !== undefined && String(bearer).trim()) {
    proxyReq.setHeader(
      'Authorization',
      `Bearer ${String(bearer).trim()}`
    );
  }
}

function pathPrefixFromServerEnv () {
  const s =
    (process.env.PROXY_SERVER && process.env.PROXY_SERVER.trim()) ||
    process.env.SERVER;
  if (!s || typeof s !== 'string') {
    return '/pipeline/';
  }
  try {
    const href = /^https?:\/\//i.test(s.trim()) ? s.trim() : `https://${s.trim()}`;
    const u = new URL(href);
    let p = u.pathname || '/';
    if (p !== '/' && !p.endsWith('/')) {
      p += '/';
    }
    return p === '/' ? '/pipeline/' : p;
  } catch (e) {
    return '/pipeline/';
  }
}

function startDevProxy () {
  const target = resolveTarget();
  const parsedListen = parseListenFromProxyServer();
  let port = parsedListen ? parsedListen.port : 9999;
  let host = parsedListen ? parsedListen.host : '127.0.0.1';
  if (process.env.PROXY_HOST && String(process.env.PROXY_HOST).trim()) {
    host = String(process.env.PROXY_HOST).trim();
  }
  if (
    process.env.PROXY_PORT !== undefined &&
    process.env.PROXY_PORT !== ''
  ) {
    const p = parseInt(process.env.PROXY_PORT, 10);
    if (!Number.isNaN(p)) {
      port = p;
    }
  }
  const secure = process.env.PROXY_INSECURE !== '1';

  const proxy = httpProxy.createProxyServer({
    target,
    changeOrigin: true,
    secure,
    xfwd: true
  });

  proxy.on('error', (err, req, res) => {
    console.error(chalk.red('[dev-proxy]'), err.message);
    if (res instanceof ServerResponse && !res.headersSent) {
      res.writeHead(502, {'Content-Type': 'text/plain'});
      res.end('Bad gateway');
    } else if (res && typeof res.destroy === 'function') {
      res.destroy();
    }
  });

  proxy.on('proxyReq', (proxyReq, req) => {
    applyUpstreamAuth(proxyReq, req, host, port, target);
  });

  proxy.on('proxyReqWs', (proxyReq, req, socket) => {
    applyUpstreamAuth(proxyReq, req, host, port, target);
    socket.on('error', () => {});
  });

  proxy.on('proxyRes', (proxyRes, req) => {
    const cors = buildCorsHeaders(req);
    Object.keys(cors).forEach((key) => {
      proxyRes.headers[key.toLowerCase()] = cors[key];
    });
  });

  const server = http.createServer((req, res) => {
    if (req.method === 'OPTIONS') {
      logVerboseProxyExchange(req, host, port, target);
      res.writeHead(204, buildCorsHeaders(req));
      res.end();
      return;
    }
    proxy.web(req, res);
  });

  server.on('upgrade', (req, socket, head) => {
    socket.on('error', () => {});
    proxy.ws(req, socket, head);
  });

  return new Promise((resolve, reject) => {
    const onListening = () => {
      server.removeListener('error', onListenError);
      const prefix = pathPrefixFromServerEnv();
      console.log(
        chalk.cyan('[dev-proxy] listening on ') +
          chalk.yellow(`http://${host}:${port}`) +
          chalk.cyan(' -> ') +
          chalk.yellow(target) +
          chalk.dim(' (HTTP + WebSocket upgrade)')
      );
      const ps = process.env.PROXY_SERVER && String(process.env.PROXY_SERVER).trim();
      if (ps) {
        console.log(
          chalk.dim(
            `[dev-proxy] Webpack dev injects PROXY_SERVER as process.env.SERVER (${ps}); upstream is SERVER / PROXY_TARGET`
          )
        );
      } else {
        console.log(
          chalk.dim(
            `Example: SERVER=http://${host}:${port}${prefix} npm start (same path prefix as SERVER in .env)`
          )
        );
      }
      const injectedNames = cookieNamesFromEnvVars();
      if (injectedNames.length) {
        console.log(
          chalk.dim(
            `[dev-proxy] PROXY_COOKIE_* env merged into upstream Cookie: ${injectedNames.join(', ')}`
          )
        );
      }
      if (
        process.env.PROXY_BEARER_TOKEN !== undefined &&
        String(process.env.PROXY_BEARER_TOKEN).trim()
      ) {
        console.log(
          chalk.dim(
            '[dev-proxy] PROXY_BEARER_TOKEN is set (upstream Authorization: Bearer …)'
          )
        );
      }
      resolve({server, port, host, target});
    };
    const onListenError = (err) => {
      reject(err);
    };
    server.once('error', onListenError);
    server.listen(port, host, onListening);
  });
}

module.exports = {startDevProxy};

if (require.main === module) {
  startDevProxy()
    .then(({server}) => {
      ['SIGINT', 'SIGTERM'].forEach((sig) => {
        process.on(sig, () => {
          server.close(() => process.exit(0));
        });
      });
    })
    .catch((err) => {
      console.error(chalk.red(err.message || err));
      process.exit(1);
    });
}
