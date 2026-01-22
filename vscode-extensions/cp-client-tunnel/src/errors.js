"use strict";
/**
 * Tunnel-specific error types matching Python pipe-cli semantics.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.TunnelProxyError = exports.TunnelConnectionError = exports.TunnelOwnerMismatchError = exports.TunnelPortOccupiedError = exports.TunnelTimeoutError = exports.TunnelError = void 0;
class TunnelError extends Error {
    constructor(message, code) {
        super(message);
        this.code = code;
        this.name = "TunnelError";
    }
}
exports.TunnelError = TunnelError;
class TunnelTimeoutError extends TunnelError {
    constructor(message) {
        super(message, "TUNNEL_TIMEOUT");
        this.name = "TunnelTimeoutError";
    }
}
exports.TunnelTimeoutError = TunnelTimeoutError;
class TunnelPortOccupiedError extends TunnelError {
    constructor(port) {
        super(`Port ${port} is already occupied`, "PORT_OCCUPIED");
        this.name = "TunnelPortOccupiedError";
    }
}
exports.TunnelPortOccupiedError = TunnelPortOccupiedError;
class TunnelOwnerMismatchError extends TunnelError {
    constructor(expectedOwner, actualOwner) {
        super(`Tunnel owned by ${actualOwner}, expected ${expectedOwner}`, "OWNER_MISMATCH");
        this.name = "TunnelOwnerMismatchError";
    }
}
exports.TunnelOwnerMismatchError = TunnelOwnerMismatchError;
class TunnelConnectionError extends TunnelError {
    constructor(message) {
        super(message, "CONNECTION_ERROR");
        this.name = "TunnelConnectionError";
    }
}
exports.TunnelConnectionError = TunnelConnectionError;
class TunnelProxyError extends TunnelError {
    constructor(message) {
        super(message, "PROXY_ERROR");
        this.name = "TunnelProxyError";
    }
}
exports.TunnelProxyError = TunnelProxyError;
//# sourceMappingURL=errors.js.map