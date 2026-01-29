import { Server, Socket, createServer } from "net";
import { Duplex } from "stream";
import { ILogger, LoggerBase } from "cp-client-common";

/**
 * TCP forwarder that listens on local port and forwards connections through proxy.
 * Based on Python pipe-cli select-based socket relay loop algorithm.
 */
export class TcpForwarder {
  private server?: Server;
  private activeConnections: Set<Socket> = new Set();
  private logger: ILogger;
  private isListening = false;

  constructor(
    private readonly localPort: number,
    private readonly createProxyStream: () => Promise<Duplex>,
    logger?: ILogger,
  ) {
    this.logger = logger || new LoggerBase();
  }

  /**
   * Start listening on local port and forward incoming connections.
   */
  async start(): Promise<number> {
    return new Promise((resolve, reject) => {
      try {
        this.server = createServer((clientSocket: Socket) => {
          this.handleClientConnection(clientSocket);
        });

        this.server.on("error", (err) => {
          this.logger.error(`TCP forwarder server error on port ${this.localPort}: ${err.message}`);
          if (!this.isListening) {
            reject(err);
          }
        });

        this.server.on("listening", () => {
          const actualPort = (this.server!.address() as any).port;
          this.isListening = true;
          this.logger.info(`Serving tunnel...`);
          this.logger.info(`Waiting for connections...`);
          resolve(actualPort);
        });

        // Listen on specified port or 0 for auto-assign
        const listenPort = this.localPort || 0;
        const bindAddress = process.env.CP_CLI_TUNNEL_SERVER_ADDRESS || "127.0.0.1";

        this.server.listen(listenPort, bindAddress);
      } catch (err) {
        this.logger.error(`Failed to start TCP forwarder: ${err}`);
        reject(err);
      }
    });
  }

  /**
   * Handle new client connection by creating proxy stream and piping data.
   */
  private async handleClientConnection(clientSocket: Socket): Promise<void> {
    const serverAddr = `${clientSocket.localAddress}:${clientSocket.localPort}`;
    const clientAddr = `${clientSocket.remoteAddress}:${clientSocket.remotePort}`;
    this.logger.debug(`New client connection ${serverAddr} <-- ${clientAddr}`);

    this.activeConnections.add(clientSocket);

    let proxyStream: Duplex | undefined;

    try {
      // Create proxy connection (HTTP CONNECT handshake)
      proxyStream = await this.createProxyStream();

      // Setup bidirectional piping (like Python's select-based relay)
      clientSocket.pipe(proxyStream);
      proxyStream.pipe(clientSocket);

      this.logger.debug(`Bidirectional pipe established for client ${clientAddr}`);

      // Handle connection close
      const cleanup = () => {
        this.logger.debug(`Closing connection for client ${clientAddr}`);
        this.activeConnections.delete(clientSocket);

        if (proxyStream && !proxyStream.destroyed) {
          proxyStream.destroy();
        }
        if (!clientSocket.destroyed) {
          clientSocket.destroy();
        }
      };

      clientSocket.on("close", cleanup);
      clientSocket.on("error", (err) => {
        this.logger.error(`Client socket error for ${clientAddr}: ${err.message}`);
        cleanup();
      });

      if (proxyStream) {
        proxyStream.on("close", cleanup);
        proxyStream.on("error", (err) => {
          this.logger.error(`Proxy stream error for ${clientAddr}: ${err.message}`);
          cleanup();
        });
      }
    } catch (err) {
      this.logger.error(`Failed to create proxy stream for client ${clientAddr}: ${err}`);

      if (proxyStream && !proxyStream.destroyed) {
        proxyStream.destroy();
      }
      if (!clientSocket.destroyed) {
        clientSocket.destroy();
      }

      this.activeConnections.delete(clientSocket);
    }
  }

  /**
   * Stop listening and close all active connections.
   */
  async stop(): Promise<void> {
    // Close all active connections
    for (const socket of this.activeConnections) {
      socket.destroy();
    }
    this.activeConnections.clear();

    // Close server
    if (this.server) {
      return new Promise((resolve) => {
        this.server!.close(() => {
          this.isListening = false;
          resolve();
        });
      });
    }
  }

  /**
   * Get the actual listening port (useful when auto-assigned with port 0).
   */
  getListeningPort(): number | undefined {
    if (this.server && this.isListening) {
      return (this.server.address() as any)?.port;
    }
    return undefined;
  }
}
