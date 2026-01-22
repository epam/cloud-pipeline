import { Duplex } from "stream";
import { ILogger } from "cp-client-common";
/**
 * Establishes HTTP CONNECT tunnel through proxy.
 * Returns Duplex stream after successful CONNECT, ready for caller to use (e.g., SSH handshake).
 * Based on Python pipe-cli http_proxy_tunnel_connect algorithm.
 */
export declare function httpProxyTunnelConnect(proxyHost: string, proxyPort: number, targetHost: string, targetPort: number, proxyUsername?: string, proxyPassword?: string, connectionTimeout?: number, logger?: ILogger): Promise<Duplex>;
