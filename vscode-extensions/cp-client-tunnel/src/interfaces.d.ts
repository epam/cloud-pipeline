import { Duplex } from "stream";
import { IDisposable, ITunnelInfo, ITunnelConfig } from "cp-client-common";
/**
 * Represents an active tunnel connection.
 */
export interface ITunnelConnection extends IDisposable {
    readonly runId: number;
    readonly localPort?: number;
    readonly remotePort: number;
    readonly pid?: number;
    readonly owner?: string;
    /**
     * Get stream after HTTP CONNECT (pre-SSH) for use by callers managing SSH themselves.
     * Returns a Duplex stream connecting through proxy to remote endpoint.
     */
    getStream(): Promise<Duplex>;
}
/**
 * Main tunnel manager API.
 */
export interface ITunnelManager extends IDisposable {
    /**
     * Start a new tunnel connection to a run.
     */
    startTunnel(runId: number, config: Partial<ITunnelConfig>): Promise<ITunnelConnection>;
    /**
     * List all active tunnel connections.
     */
    listTunnels(): Promise<ITunnelInfo[]>;
    /**
     * Stop a running tunnel by runId or localPort.
     */
    stopTunnel(runId?: number, localPort?: number): Promise<void>;
}
