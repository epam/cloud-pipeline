import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { URL } from 'url';
import * as vscode from 'vscode';

const CURSOR_MCP_JSON = path.join(os.homedir(), '.cursor', 'mcp.json');
const CLAUDE_JSON = path.join(os.homedir(), '.claude.json');

export interface McpJsonRoot {
  mcpServers?: Record<string, Record<string, unknown>>;
  [key: string]: unknown;
}

function mcpStreamableHttpUrl(serverUrl: string): string {
  const t = serverUrl.trim().replace(/\/$/, '');
  if (t.endsWith('/mcp')) {
    return t;
  }
  return `${t}/mcp`;
}

/**
 * Derive the MCP base URL from the Cloud Pipeline API URL by stripping its path.
 * Example: https://cp.example.com/pipeline/restapi -> https://cp.example.com
 */
export function deriveMcpServerUrlFromApi(apiUrl: string): string | undefined {
  const trimmed = (apiUrl ?? '').trim();
  if (!trimmed) {
    return undefined;
  }
  try {
    const u = new URL(trimmed);
    return `${u.protocol}//${u.host}`;
  } catch {
    return undefined;
  }
}

export function effectiveMcpServerUrl(apiUrl: string): string | undefined {
  const cfg = vscode.workspace.getConfiguration('cloudPipeline');
  const explicit = (cfg.get<string>('mcp.serverUrl') ?? '').trim();
  if (explicit) {
    return explicit;
  }
  return deriveMcpServerUrlFromApi(apiUrl);
}

function readJsonFile(filePath: string): McpJsonRoot {
  try {
    if (!fs.existsSync(filePath)) {
      return {};
    }
    const raw = fs.readFileSync(filePath, 'utf8');
    const data = JSON.parse(raw) as unknown;
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      return data as McpJsonRoot;
    }
  } catch {
    /* ignore */
  }
  return {};
}

function writeJsonFile(filePath: string, root: McpJsonRoot): void {
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  }
  const fd = fs.openSync(filePath, 'w', 0o600);
  try {
    fs.writeFileSync(fd, `${JSON.stringify(root, null, 2)}\n`, { mode: 0o600 });
  } finally {
    fs.closeSync(fd);
  }
  try {
    fs.chmodSync(filePath, 0o600);
  } catch {
    /* ignore */
  }
}

function buildStdioEntry(proxyScript: string, mcpEndpoint: string, apiBase: string, bearerToken: string): Record<string, unknown> {
  return {
    command: process.execPath,
    args: [proxyScript],
    env: {
      CP_MCP_URL: mcpEndpoint,
      CP_API_BASE: apiBase,
      CP_TOKEN: bearerToken,
    },
  };
}

/**
 * Merge ~/.cursor/mcp.json and ~/.claude.json with a stdio proxy entry
 * that forwards MCP traffic to the remote HTTP server.
 */
export function writeRemoteCloudPipelineMcpEntry(params: {
  extensionPath: string;
  serverUrl: string;
  apiBase: string;
  bearerToken: string;
  serverId: string;
}): void {
  const mcpEndpoint = mcpStreamableHttpUrl(params.serverUrl);
  const apiBase = params.apiBase.trim().replace(/\/$/, '');
  const proxyScript = path.join(params.extensionPath, 'out', 'mcpStdioProxy.js');
  const entry = buildStdioEntry(proxyScript, mcpEndpoint, apiBase, params.bearerToken);

  // Cursor: ~/.cursor/mcp.json
  const cursorRoot = readJsonFile(CURSOR_MCP_JSON);
  const cursorServers = { ...(cursorRoot.mcpServers ?? {}) };
  cursorServers[params.serverId] = entry;
  writeJsonFile(CURSOR_MCP_JSON, { ...cursorRoot, mcpServers: cursorServers });

  // Claude Code: ~/.claude.json (user-scoped mcpServers)
  const claudeRoot = readJsonFile(CLAUDE_JSON);
  const claudeServers = { ...(claudeRoot.mcpServers ?? {}) };
  claudeServers[params.serverId] = entry;
  writeJsonFile(CLAUDE_JSON, { ...claudeRoot, mcpServers: claudeServers });
}

export function syncMcpFromWorkspaceSettings(
  auth: { apiUrl: string; accessKey: string },
  extensionPath: string
): boolean {
  const serverUrl = effectiveMcpServerUrl(auth.apiUrl);
  if (!serverUrl) {
    return false;
  }
  const cfg = vscode.workspace.getConfiguration('cloudPipeline');
  const serverId = (cfg.get<string>('mcp.serverId') ?? 'cloud-pipeline').trim() || 'cloud-pipeline';
  writeRemoteCloudPipelineMcpEntry({
    extensionPath,
    serverUrl,
    apiBase: auth.apiUrl,
    bearerToken: auth.accessKey,
    serverId,
  });
  return true;
}
