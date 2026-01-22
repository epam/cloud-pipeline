"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TunnelConnection = void 0;
const cp_client_common_1 = require("cp-client-common");
/**
 * Represents an active tunnel connection.
 * Manages lifecycle and provides access to underlying socket/stream.
 */
class TunnelConnection extends cp_client_common_1.Disposable {
    constructor(runId, config, getProxyStream) {
        super();
        this.getProxyStream = getProxyStream;
        this.streamInitialized = false;
        this.runId = runId;
        this.remotePort = config.remotePort;
        this.localPort = config.localPort;
    }
    /**
     * Get the underlying stream (post-HTTP CONNECT, pre-SSH).
     * Lazily initialized on first call.
     */
    async getStream() {
        if (!this.streamInitialized) {
            this.proxyStream = await this.getProxyStream();
            this.streamInitialized = true;
        }
        return this.proxyStream;
    }
    dispose() {
        if (this.proxyStream) {
            this.proxyStream.destroy();
        }
        super.dispose();
    }
}
exports.TunnelConnection = TunnelConnection;
//# sourceMappingURL=tunnel-connection.js.map