// Public API
export {
  type ITunnelConnection,
  type ITunnelManager,
  type Endpoint,
  type ProxyEndpoint,
  parseProxyUrl
} from "./common";

export { TunnelManager, type TunnelManagerConfig } from "./tunnel-manager";
export { TunnelConnection } from "./tunnel-connection";
export { TcpForwarder } from "./tcp-forwarder";

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
export {
  getRunConnectionInfo,
  getCustomConnectionInfo,
  type RunConnectionInfo
} from "./connection-info";
