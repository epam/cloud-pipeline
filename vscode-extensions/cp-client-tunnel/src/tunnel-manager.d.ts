import { ITunnelConnection, ITunnelManager } from "./interfaces";
import { Disposable, ILogger, ITunnelConfig, ITunnelInfo } from "cp-client-common";
export interface TunnelManagerConfig {
    proxyHost: string;
    proxyPort: number;
    proxyUsername?: string;
    proxyPassword?: string;
    connectionTimeout?: number;
    logger?: ILogger;
}
/**
 * Main tunnel manager implementation.
 * Provides startTunnel, listTunnels, stopTunnel API.
 */
export declare class TunnelManager extends Disposable implements ITunnelManager {
    private config;
    private logger;
    private activeTunnels;
    constructor(config: TunnelManagerConfig);
    startTunnel(runId: number, config: Partial<ITunnelConfig>): Promise<ITunnelConnection>;
    listTunnels(): Promise<ITunnelInfo[]>;
    stopTunnel(runId?: number, localPort?: number): Promise<void>;
    dispose(): void;
}
