// Public API
export {
  parseProxyUrl
} from "./common";

export {
  type Endpoint,
  type ProxyEndpoint,
  type ITunnelManagerConfig,
  type ITunnelConnection,
  type ITunnelManager,
} from "./types";

export { TunnelManager, } from "./tunnel-manager";
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
