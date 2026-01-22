import { ITunnelConnection } from "./interfaces";
import { Disposable, ITunnelConfig } from "cp-client-common";
import { Duplex } from "stream";
/**
 * Represents an active tunnel connection.
 * Manages lifecycle and provides access to underlying socket/stream.
 */
export declare class TunnelConnection extends Disposable implements ITunnelConnection {
    private readonly getProxyStream;
    readonly runId: number;
    readonly remotePort: number;
    readonly localPort?: number;
    readonly pid?: number;
    readonly owner?: string;
    private proxyStream?;
    private streamInitialized;
    constructor(runId: number, config: ITunnelConfig, getProxyStream: () => Promise<Duplex>);
    /**
     * Get the underlying stream (post-HTTP CONNECT, pre-SSH).
     * Lazily initialized on first call.
     */
    getStream(): Promise<Duplex>;
    dispose(): void;
}
