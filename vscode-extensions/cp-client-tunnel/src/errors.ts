/**
 * Tunnel-specific error types matching Python pipe-cli semantics.
 */

export class TunnelError extends Error {
  constructor(message: string, public readonly code?: string) {
    super(message);
    this.name = "TunnelError";
  }
}

export class TunnelTimeoutError extends TunnelError {
  constructor(message: string) {
    super(message, "TUNNEL_TIMEOUT");
    this.name = "TunnelTimeoutError";
  }
}

export class TunnelPortOccupiedError extends TunnelError {
  constructor(port: number) {
    super(`Port ${port} is already occupied`, "PORT_OCCUPIED");
    this.name = "TunnelPortOccupiedError";
  }
}

export class TunnelOwnerMismatchError extends TunnelError {
  constructor(expectedOwner: string, actualOwner: string) {
    super(
      `Tunnel owned by ${actualOwner}, expected ${expectedOwner}`,
      "OWNER_MISMATCH",
    );
    this.name = "TunnelOwnerMismatchError";
  }
}

export class TunnelConnectionError extends TunnelError {
  constructor(message: string) {
    super(message, "CONNECTION_ERROR");
    this.name = "TunnelConnectionError";
  }
}

export class TunnelProxyError extends TunnelError {
  constructor(message: string) {
    super(message, "PROXY_ERROR");
    this.name = "TunnelProxyError";
  }
}
