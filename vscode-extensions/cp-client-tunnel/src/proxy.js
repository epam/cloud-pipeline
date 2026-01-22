"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.httpProxyTunnelConnect = httpProxyTunnelConnect;
const net_1 = require("net");
const errors_1 = require("./errors");
/**
 * Establishes HTTP CONNECT tunnel through proxy.
 * Returns Duplex stream after successful CONNECT, ready for caller to use (e.g., SSH handshake).
 * Based on Python pipe-cli http_proxy_tunnel_connect algorithm.
 */
async function httpProxyTunnelConnect(proxyHost, proxyPort, targetHost, targetPort, proxyUsername, proxyPassword, connectionTimeout, logger) {
    logger?.debug(`HTTP CONNECT proxy: ${proxyHost}:${proxyPort} -> ${targetHost}:${targetPort}`);
    return new Promise((resolve, reject) => {
        let socket;
        const timeoutMs = (connectionTimeout ?? 30) * 1000;
        let connectTimeout;
        const cleanup = (error) => {
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
            socket = (0, net_1.createConnection)({
                host: proxyHost,
                port: proxyPort,
                timeout: timeoutMs,
            });
            connectTimeout = setTimeout(() => {
                cleanup(new errors_1.TunnelConnectionError("Proxy connection timeout"));
            }, timeoutMs);
            socket.on("error", (err) => {
                logger?.error(`Proxy socket error: ${err.message}`);
                cleanup(new errors_1.TunnelConnectionError(`Proxy connection error: ${err.message}`));
            });
            socket.on("connect", () => {
                logger?.debug(`Connected to proxy ${proxyHost}:${proxyPort}`);
                // Send HTTP CONNECT request
                let connectReq = `CONNECT ${targetHost}:${targetPort} HTTP/1.0\r\n`;
                connectReq += "User-Agent: pipe-tunnel/nodejs\r\n";
                connectReq += "Connection: close\r\n";
                if (proxyUsername && proxyPassword) {
                    const auth = Buffer.from(`${proxyUsername}:${proxyPassword}`).toString("base64");
                    connectReq += `Proxy-Authorization: Basic ${auth}\r\n`;
                }
                connectReq += "\r\n";
                logger?.debug(`Sending HTTP CONNECT: ${targetHost}:${targetPort}`);
                socket.write(connectReq);
            });
            // Wait for HTTP response
            let responseData = "";
            socket.on("data", (chunk) => {
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
                        socket.removeAllListeners("data");
                        // If there's extra data after headers, it needs to be handled
                        const extra = responseData.substring(headerEnd + 4);
                        if (extra) {
                            logger?.trace(`Extra data after headers: ${extra.length} bytes`);
                            // Prepend extra data back to stream if needed by downstream
                        }
                        if (connectTimeout) {
                            clearTimeout(connectTimeout);
                        }
                        resolve(socket);
                    }
                    else {
                        logger?.error(`Proxy returned non-200: ${headers.split("\n")[0]}`);
                        cleanup(new errors_1.TunnelProxyError(`Proxy CONNECT failed: ${headers.split("\n")[0]}`));
                    }
                }
            });
        }
        catch (err) {
            cleanup(err instanceof Error ? err : new Error(String(err)));
        }
    });
}
//# sourceMappingURL=proxy.js.map