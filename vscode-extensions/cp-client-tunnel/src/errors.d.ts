/**
 * Tunnel-specific error types matching Python pipe-cli semantics.
 */
export declare class TunnelError extends Error {
    readonly code?: string | undefined;
    constructor(message: string, code?: string | undefined);
}
export declare class TunnelTimeoutError extends TunnelError {
    constructor(message: string);
}
export declare class TunnelPortOccupiedError extends TunnelError {
    constructor(port: number);
}
export declare class TunnelOwnerMismatchError extends TunnelError {
    constructor(expectedOwner: string, actualOwner: string);
}
export declare class TunnelConnectionError extends TunnelError {
    constructor(message: string);
}
export declare class TunnelProxyError extends TunnelError {
    constructor(message: string);
}
