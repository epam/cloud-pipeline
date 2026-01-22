"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TunnelManager = void 0;
const cp_client_common_1 = require("cp-client-common");
const tunnel_connection_1 = require("./tunnel-connection");
const proxy_1 = require("./proxy");
const process_discovery_1 = require("./process-discovery");
/**
 * Main tunnel manager implementation.
 * Provides startTunnel, listTunnels, stopTunnel API.
 */
class TunnelManager extends cp_client_common_1.Disposable {
    constructor(config) {
        super();
        this.config = config;
        this.activeTunnels = new Map();
        this.logger = config.logger || new cp_client_common_1.LoggerBase();
    }
    async startTunnel(runId, config) {
        this.logger.info(`Starting tunnel to run ${runId}`);
        // Create tunnel connection with proxy stream factory
        const tunnelConfig = {
            runId,
            remotePort: config.remotePort || 22,
            localPort: config.localPort,
            region: config.region,
            direct: config.direct,
            ssh: config.ssh,
        };
        const connection = new tunnel_connection_1.TunnelConnection(runId, tunnelConfig, async () => {
            // Factory function to create proxy stream on demand
            return (0, proxy_1.httpProxyTunnelConnect)(this.config.proxyHost, this.config.proxyPort, "127.0.0.1", // Target host (will be overridden by caller)
            tunnelConfig.remotePort, this.config.proxyUsername, this.config.proxyPassword, this.config.connectionTimeout, this.logger);
        });
        this._register(connection);
        this.activeTunnels.set(runId, connection);
        this.logger.info(`Tunnel to run ${runId} started (connection created)`);
        return connection;
    }
    async listTunnels() {
        this.logger.debug("Listing active tunnels");
        return (0, process_discovery_1.findExistingTunnels)(this.logger);
    }
    async stopTunnel(runId, localPort) {
        if (runId !== undefined) {
            const conn = this.activeTunnels.get(runId);
            if (conn) {
                this.logger.info(`Stopping tunnel for run ${runId}`);
                conn.dispose();
                this.activeTunnels.delete(runId);
            }
        }
        else if (localPort !== undefined) {
            this.logger.info(`Stopping tunnel on local port ${localPort}`);
            // TODO: Find and stop tunnel by local port
        }
        else {
            this.logger.warn("stopTunnel called without runId or localPort");
        }
    }
    dispose() {
        this.logger.info("Disposing tunnel manager");
        this.activeTunnels.forEach((conn) => conn.dispose());
        this.activeTunnels.clear();
        super.dispose();
    }
}
exports.TunnelManager = TunnelManager;
//# sourceMappingURL=tunnel-manager.js.map