import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { URL } from 'url';
import * as vscode from 'vscode';

const MCP_JSON = path.join(os.homedir(), '.cursor', 'mcp.json');

export interface McpJsonRoot {
  mcpServers?: Record<string, Record<string, unknown>>;
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

function readMcpJson(): McpJsonRoot {
  try {
    if (!fs.existsSync(MCP_JSON)) {
      return {};
    }
    const raw = fs.readFileSync(MCP_JSON, 'utf8');
    const data = JSON.parse(raw) as unknown;
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      return data as McpJsonRoot;
    }
  } catch {
    /* ignore */
  }
  return {};
}

function writeMcpJson(root: McpJsonRoot): void {
  const dir = path.dirname(MCP_JSON);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  }
  const fd = fs.openSync(MCP_JSON, 'w', 0o600);
  try {
    fs.writeFileSync(fd, `${JSON.stringify(root, null, 2)}\n`, { mode: 0o600 });
  } finally {
    fs.closeSync(fd);
  }
  try {
    fs.chmodSync(MCP_JSON, 0o600);
  } catch {
    /* ignore */
  }
}

/**
 * Merge ~/.cursor/mcp.json so Cursor sends JWT and API base to the remote MCP server on each request.
 */
export function writeRemoteCloudPipelineMcpEntry(params: {
  serverUrl: string;
  apiBase: string;
  bearerToken: string;
  serverId: string;
}): void {
  const url = mcpStreamableHttpUrl(params.serverUrl);
  const apiBase = params.apiBase.trim().replace(/\/$/, '');
  const root = readMcpJson();
  const servers = { ...(root.mcpServers ?? {}) };
  servers[params.serverId] = {
    url,
    headers: {
      Authorization: `Bearer ${params.bearerToken}`,
      'X-Cloud-Pipeline-Api-Base': apiBase,
    },
  };
  writeMcpJson({ ...root, mcpServers: servers });
}

export function syncMcpFromWorkspaceSettings(auth: {
  apiUrl: string;
  accessKey: string;
}): boolean {
  const serverUrl = effectiveMcpServerUrl(auth.apiUrl);
  if (!serverUrl) {
    return false;
  }
  const cfg = vscode.workspace.getConfiguration('cloudPipeline');
  const serverId = (cfg.get<string>('mcp.serverId') ?? 'cloud-pipeline').trim() || 'cloud-pipeline';
  writeRemoteCloudPipelineMcpEntry({
    serverUrl,
    apiBase: auth.apiUrl,
    bearerToken: auth.accessKey,
    serverId,
  });
  return true;
}
