"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.findExistingTunnels = exports.httpProxyTunnelConnect = exports.TunnelProxyError = exports.TunnelConnectionError = exports.TunnelOwnerMismatchError = exports.TunnelPortOccupiedError = exports.TunnelTimeoutError = exports.TunnelError = exports.TunnelConnection = exports.TunnelManager = void 0;
var tunnel_manager_1 = require("./tunnel-manager");
Object.defineProperty(exports, "TunnelManager", { enumerable: true, get: function () { return tunnel_manager_1.TunnelManager; } });
var tunnel_connection_1 = require("./tunnel-connection");
Object.defineProperty(exports, "TunnelConnection", { enumerable: true, get: function () { return tunnel_connection_1.TunnelConnection; } });
// Errors
var errors_1 = require("./errors");
Object.defineProperty(exports, "TunnelError", { enumerable: true, get: function () { return errors_1.TunnelError; } });
Object.defineProperty(exports, "TunnelTimeoutError", { enumerable: true, get: function () { return errors_1.TunnelTimeoutError; } });
Object.defineProperty(exports, "TunnelPortOccupiedError", { enumerable: true, get: function () { return errors_1.TunnelPortOccupiedError; } });
Object.defineProperty(exports, "TunnelOwnerMismatchError", { enumerable: true, get: function () { return errors_1.TunnelOwnerMismatchError; } });
Object.defineProperty(exports, "TunnelConnectionError", { enumerable: true, get: function () { return errors_1.TunnelConnectionError; } });
Object.defineProperty(exports, "TunnelProxyError", { enumerable: true, get: function () { return errors_1.TunnelProxyError; } });
// Utilities
var proxy_1 = require("./proxy");
Object.defineProperty(exports, "httpProxyTunnelConnect", { enumerable: true, get: function () { return proxy_1.httpProxyTunnelConnect; } });
var process_discovery_1 = require("./process-discovery");
Object.defineProperty(exports, "findExistingTunnels", { enumerable: true, get: function () { return process_discovery_1.findExistingTunnels; } });
//# sourceMappingURL=index.js.map