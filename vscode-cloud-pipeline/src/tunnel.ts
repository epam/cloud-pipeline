import * as net from 'net';

export interface TunnelTarget {
  /** When direct, connect TCP to this host:port */
  directHost?: string;
  directPort?: number;
  /** When not direct, HTTP CONNECT proxy */
  proxyHost?: string;
  proxyPort?: number;
  /** CONNECT target host:port (pod IP and remote port) */
  remoteHost: string;
  remotePort: number;
  /** Basic auth user for Proxy-Authorization */
  proxyAuthUser: string;
  /** Basic auth password (access key) */
  proxyAuthPassword: string;
}

function base64Basic(user: string, pass: string): string {
  return Buffer.from(`${user}:${pass}`, 'utf8').toString('base64');
}

export function httpProxyTunnelConnect(
  proxyHost: string,
  proxyPort: number,
  targetHost: string,
  targetPort: number,
  proxyAuthUser: string,
  proxyAuthPassword: string,
  connectionTimeoutMs = 30000
): Promise<net.Socket> {
  return new Promise((resolve, reject) => {
    const sock = net.createConnection({ host: proxyHost, port: proxyPort }, () => {
      const auth = base64Basic(proxyAuthUser, proxyAuthPassword);
      const req =
        `CONNECT ${targetHost}:${targetPort} HTTP/1.0\r\n` +
        `Proxy-Authorization: Basic ${auth}\r\n` +
        `\r\n`;
      sock.write(req);
    });

    const timer = setTimeout(() => {
      sock.destroy();
      reject(new Error('CONNECT timeout'));
    }, connectionTimeoutMs);

    let buf = '';
    const onData = (chunk: Buffer) => {
      buf += chunk.toString('utf8');
      if (buf.includes('\r\n\r\n')) {
        clearTimeout(timer);
        sock.off('data', onData);
        const lower = buf.toLowerCase();
        if (!lower.includes('200 connection established')) {
          sock.destroy();
          reject(new Error(`CONNECT failed: ${buf.slice(0, 300)}`));
          return;
        }
        resolve(sock);
      }
    };
    sock.on('data', onData);
    sock.on('error', (e) => {
      clearTimeout(timer);
      reject(e);
    });
  });
}

function directConnect(host: string, port: number, connectionTimeoutMs: number): Promise<net.Socket> {
  return new Promise((resolve, reject) => {
    const sock = net.createConnection({ host, port }, () => resolve(sock));
    sock.setTimeout(connectionTimeoutMs, () => {
      sock.destroy();
      reject(new Error('Direct connect timeout'));
    });
    sock.on('error', reject);
  });
}

function pipeSockets(a: net.Socket, b: net.Socket): void {
  a.pipe(b);
  b.pipe(a);
  const onEnd = () => {
    try {
      a.destroy();
      b.destroy();
    } catch {
      /* ignore */
    }
  };
  a.on('close', onEnd);
  b.on('close', onEnd);
  a.on('error', onEnd);
  b.on('error', onEnd);
}

export interface TunnelServerHandle {
  readonly localPort: number;
  close(): Promise<void>;
}

/**
 * Listens on 127.0.0.1:0; each incoming connection opens a tunnel to remotePort on remoteHost.
 */
export async function startLocalTunnelServer(target: TunnelTarget): Promise<TunnelServerHandle> {
  const server = net.createServer();
  await new Promise<void>((resolve, reject) => {
    server.listen(0, '127.0.0.1', () => resolve());
    server.on('error', reject);
  });
  const addr = server.address() as net.AddressInfo;
  const localPort = addr.port;

  server.on('connection', (clientSocket) => {
    (async () => {
      let remoteSocket: net.Socket;
      if (target.directHost && target.directPort !== undefined) {
        remoteSocket = await directConnect(target.directHost, target.directPort, 30000);
      } else if (target.proxyHost && target.proxyPort !== undefined) {
        remoteSocket = await httpProxyTunnelConnect(
          target.proxyHost,
          target.proxyPort,
          target.remoteHost,
          target.remotePort,
          target.proxyAuthUser,
          target.proxyAuthPassword
        );
      } else {
        clientSocket.destroy();
        return;
      }
      pipeSockets(clientSocket, remoteSocket);
    })().catch(() => {
      try {
        clientSocket.destroy();
      } catch {
        /* ignore */
      }
    });
  });

  return {
    localPort,
    close: () =>
      new Promise<void>((resolve) => {
        const s = server as NodeJS.EventEmitter & { closeAllConnections?: () => void };
        s.closeAllConnections?.();
        server.close(() => resolve());
      }),
  };
}

export function parseEdgeUrl(externalUrl: string): { host: string; port: number } {
  let u: URL;
  try {
    u = new URL(externalUrl);
  } catch {
    u = new URL(externalUrl.startsWith('http') ? externalUrl : `https://${externalUrl}`);
  }
  const host = u.hostname;
  const port = u.port ? parseInt(u.port, 10) : u.protocol === 'https:' ? 443 : 80;
  if (!host) {
    throw new Error('Invalid EDGE URL');
  }
  return { host, port };
}
