// Public API
export { ITunnelConnection, ITunnelManager } from "./interfaces";
export { TunnelManager, type TunnelManagerConfig } from "./tunnel-manager";
export { TunnelConnection } from "./tunnel-connection";

// Errors
export {
  TunnelError,
  TunnelTimeoutError,
  TunnelPortOccupiedError,
  TunnelOwnerMismatchError,
  TunnelConnectionError,
  TunnelProxyError,
} from "./errors";

// Utilities
export { httpProxyTunnelConnect } from "./proxy";
export { findExistingTunnels } from "./process-discovery";
