import { Socket, createConnection } from "net";
import { Duplex } from "stream";
import { ILogger, LoggerBase } from "cp-client-common";
import { TunnelProxyError, TunnelConnectionError } from "./errors";

/**
 * Establishes HTTP CONNECT tunnel through proxy.
 * Returns Duplex stream after successful CONNECT, ready for caller to use (e.g., SSH handshake).
 * Based on Python pipe-cli http_proxy_tunnel_connect algorithm.
 */
export async function httpProxyTunnelConnect(
  proxyHost: string,
  proxyPort: number,
  targetHost: string,
  targetPort: number,
  proxyUsername?: string,
  proxyPassword?: string,
  connectionTimeout?: number,
  logger?: ILogger,
): Promise<Duplex> {
  logger?.debug(
    `HTTP CONNECT proxy: ${proxyHost}:${proxyPort} -> ${targetHost}:${targetPort}`,
  );

  return new Promise((resolve, reject) => {
    let socket: Socket | undefined;
    const timeoutMs = (connectionTimeout ?? 30) * 1000;
    let connectTimeout: NodeJS.Timeout | undefined;

    const cleanup = (error?: Error) => {
      if (connectTimeout) {
        clearTimeout(connectTimeout);
      }
      if (socket) {
        socket.destroy();
      }
      if (error) {
        reject(error);
      }
    };

    try {
      socket = createConnection({
        host: proxyHost,
        port: proxyPort,
        timeout: timeoutMs,
      });

      connectTimeout = setTimeout(() => {
        cleanup(new TunnelConnectionError("Proxy connection timeout"));
      }, timeoutMs);

      socket.on("error", (err) => {
        logger?.error(`Proxy socket error: ${err.message}`);
        cleanup(new TunnelConnectionError(`Proxy connection error: ${err.message}`));
      });

      socket.on("connect", () => {
        logger?.debug(`Connected to proxy ${proxyHost}:${proxyPort}`);

        // Send HTTP CONNECT request
        let connectReq = `CONNECT ${targetHost}:${targetPort} HTTP/1.0\r\n`;
        connectReq += "User-Agent: pipe-tunnel/nodejs\r\n";
        connectReq += "Connection: close\r\n";

        if (proxyUsername && proxyPassword) {
          const auth = Buffer.from(`${proxyUsername}:${proxyPassword}`).toString(
            "base64",
          );
          connectReq += `Proxy-Authorization: Basic ${auth}\r\n`;
        }

        connectReq += "\r\n";

        logger?.debug(`Sending HTTP CONNECT: ${targetHost}:${targetPort}`);
        socket!.write(connectReq);
      });

      // Wait for HTTP response
      let responseData = "";
      socket.on("data", (chunk: Buffer) => {
        responseData += chunk.toString();
        logger?.trace(`Proxy response: ${chunk.toString()}`);

        // Look for end of headers (blank line)
        if (responseData.includes("\r\n\r\n")) {
          const headerEnd = responseData.indexOf("\r\n\r\n");
          const headers = responseData.substring(0, headerEnd);

          // Check for 200 response
          if (headers.includes("200")) {
            logger?.debug("HTTP CONNECT successful (200)");

            // Remove listeners for data events and use socket as raw stream
            socket!.removeAllListeners("data");

            // If there's extra data after headers, it needs to be handled
            const extra = responseData.substring(headerEnd + 4);
            if (extra) {
              logger?.trace(`Extra data after headers: ${extra.length} bytes`);
              // Prepend extra data back to stream if needed by downstream
            }

            if (connectTimeout) {
              clearTimeout(connectTimeout);
            }

            resolve(socket as Duplex);
          } else {
            logger?.error(`Proxy returned non-200: ${headers.split("\n")[0]}`);
            cleanup(
              new TunnelProxyError(
                `Proxy CONNECT failed: ${headers.split("\n")[0]}`,
              ),
            );
          }
        }
      });
    } catch (err) {
      cleanup(err instanceof Error ? err : new Error(String(err)));
    }
  });
}
