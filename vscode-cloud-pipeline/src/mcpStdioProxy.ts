import * as http from 'http';
import * as https from 'https';
import { URL } from 'url';

interface JsonRpcMessage {
  jsonrpc: '2.0';
  id?: string | number | null;
  method?: string;
  params?: unknown;
  result?: unknown;
  error?: { code: number; message: string; data?: unknown };
}

const mcpUrl = (process.env.CP_MCP_URL ?? '').trim();
const apiBase = (process.env.CP_API_BASE ?? '').trim();
const token = (process.env.CP_TOKEN ?? '').trim();

let currentSessionId: string | undefined;
let pendingRequests = 0;
let stdinEnded = false;

function writeResponse(id: string | number | null, error: { code: number; message: string }): void {
  const msg = JSON.stringify({ jsonrpc: '2.0', id, error });
  process.stdout.write(msg + '\n');
}

function postToMcp(
  body: string
): Promise<{ statusCode: number; text: string; sessionId?: string }> {
  return new Promise((resolve, reject) => {
    const u = new URL(mcpUrl);
    const isHttps = u.protocol === 'https:';
    const lib = isHttps ? https : http;
    const reqHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json, text/event-stream',
      Authorization: `Bearer ${token}`,
      'X-Cloud-Pipeline-Api-Base': apiBase,
    };
    if (currentSessionId) {
      reqHeaders['Mcp-Session-Id'] = currentSessionId;
    }
    const opts: https.RequestOptions = {
      method: 'POST',
      hostname: u.hostname,
      port: u.port || (isHttps ? 443 : 80),
      path: u.pathname + u.search,
      headers: { ...reqHeaders, 'Content-Length': Buffer.byteLength(body) },
      rejectUnauthorized: false,
    };
    const req = lib.request(opts, (res) => {
      const chunks: Buffer[] = [];
      res.on('data', (c: Buffer) => chunks.push(c));
      res.on('end', () => {
        resolve({
          statusCode: res.statusCode ?? 0,
          text: Buffer.concat(chunks).toString('utf8'),
          sessionId: (res.headers['mcp-session-id'] as string) || undefined,
        });
      });
    });
    req.setTimeout(120_000, () => {
      req.destroy(new Error('MCP request timed out'));
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function maybeExit(): void {
  if (stdinEnded && pendingRequests === 0) {
    process.exit(0);
  }
}

async function handleLine(line: string): Promise<void> {
  let msg: JsonRpcMessage;
  try {
    msg = JSON.parse(line) as JsonRpcMessage;
  } catch {
    writeResponse(null, { code: -32700, message: 'Parse error' });
    return;
  }

  const isRequest = 'id' in msg && msg.id !== undefined && msg.id !== null;

  pendingRequests++;
  try {
    const result = await postToMcp(line);

    if (result.sessionId) {
      currentSessionId = result.sessionId;
    }

    if (!isRequest) {
      return;
    }

    if (result.statusCode === 200 && result.text.trim()) {
      process.stdout.write(result.text.trim() + '\n');
    } else if (result.statusCode !== 202) {
      writeResponse(msg.id!, {
        code: -32000,
        message: `HTTP ${result.statusCode}: ${result.text.slice(0, 200)}`,
      });
    }
  } catch (e) {
    if (isRequest) {
      writeResponse(msg.id!, {
        code: -32000,
        message: e instanceof Error ? e.message : String(e),
      });
    }
    process.stderr.write(
      `[mcpStdioProxy] error: ${e instanceof Error ? e.message : String(e)}\n`
    );
  } finally {
    pendingRequests--;
    maybeExit();
  }
}

function main(): void {
  if (!mcpUrl || !apiBase || !token) {
    process.stderr.write(
      '[mcpStdioProxy] Missing required env vars: CP_MCP_URL, CP_API_BASE, CP_TOKEN\n'
    );
    process.exit(1);
  }

  let buffer = '';

  process.stdin.setEncoding('utf8');
  process.stdin.on('data', (chunk: string) => {
    buffer += chunk;
    const lines = buffer.split('\n');
    buffer = lines.pop() ?? '';
    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed) {
        void handleLine(trimmed);
      }
    }
  });

  process.stdin.on('end', () => {
    const trimmed = buffer.trim();
    if (trimmed) {
      void handleLine(trimmed);
    }
    stdinEnded = true;
    maybeExit();
  });

  process.stdin.on('error', () => process.exit(0));
}

if (require.main === module) {
  main();
}
