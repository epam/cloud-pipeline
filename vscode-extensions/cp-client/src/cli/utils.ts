/**
 * Utility functions for CLI operations
 */

import { TunnelManagerConfig } from "cp-client-tunnel";
import { FileLogger } from "cp-client-common";
import * as path from "path";
import * as os from "os";
import * as net from "net";
import { spawn } from "child_process";
import { createWriteStream, mkdirSync } from "fs";
import { GlobalOptions } from "./types";

/**
 * Create tunnel manager configuration from CLI options
 */
export function createTunnelManagerConfig(
  opts: GlobalOptions,
): TunnelManagerConfig {
  const logLevel = opts.debug ? "debug" : opts.trace ? "trace" : "info";
  const logDir = path.join(os.homedir(), ".pipe", "logs");
  const logFile = path.join(logDir, "tunnel.log");

  return {
    proxyHost: process.env.CP_PLATFORM_URL || "aws.cloud-pipeline.com",
    proxyPort: 443,
    connectionTimeout: 30,
    logger: new FileLogger(logFile, logLevel as any),
  };
}

/**
 * Check if a port is being listened on
 */
export async function isPortListening(port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    socket.setTimeout(100);
    socket.once("connect", () => {
      socket.destroy();
      resolve(true);
    });
    socket.once("timeout", () => {
      socket.destroy();
      resolve(false);
    });
    socket.once("error", () => {
      socket.destroy();
      resolve(false);
    });
    socket.connect(port, "127.0.0.1");
  });
}

/**
 * Wait for background tunnel to be ready
 */
export async function waitForBackgroundTunnel(
  childPid: number,
  localPort: number,
  timeoutSeconds: number,
): Promise<void> {
  const pollingDelay = 1000; // 1 second
  const attempts = timeoutSeconds;

  console.log(
    `Waiting for tunnel process #${childPid} to listen on port ${localPort}...`,
  );

  for (let i = 0; i < attempts; i++) {
    await new Promise((resolve) => setTimeout(resolve, pollingDelay));

    // Check if process is still alive
    try {
      process.kill(childPid, 0); // Signal 0 checks if process exists
    } catch (err) {
      throw new Error(
        `Background tunnel process #${childPid} has exited unexpectedly`,
      );
    }

    // Check if port is listening
    if (await isPortListening(localPort)) {
      console.log("Background tunnel is initialized.");
      return;
    }
  }

  throw new Error(
    `Failed to serve tunnel in background. Tunnel is not initialized after ${timeoutSeconds} seconds.`,
  );
}

/**
 * Start tunnel in background by spawning detached process
 */
export function startBackgroundTunnel(
  logFile: string,
  timeout: number,
): Promise<number> {
  return new Promise((resolve, reject) => {
    // Ensure log directory exists
    const logDir = path.dirname(logFile);
    mkdirSync(logDir, { recursive: true });

    // Filter out conflict args
    const conflictArgs = [
      "-ke",
      "--keep-existing",
      "-ks",
      "--keep-same",
      "-re",
      "--replace-existing",
      "-rd",
      "--replace-different",
    ];

    const args = process.argv
      .slice(2)
      .filter((arg) => !conflictArgs.includes(arg));

    // Add foreground and ignore-existing flags
    args.push("--foreground", "--ignore-existing");

    // Open log file for output
    const logStream = createWriteStream(logFile, { flags: "a" });

    // Spawn detached process
    const spawnOptions: any = {
      detached: true,
      stdio: ["ignore", logStream, logStream],
      cwd: process.cwd(),
      env: process.env,
    };

    // On Windows, use specific flags for detached process
    if (process.platform === "win32") {
      spawnOptions.windowsHide = true;
    }

    const child = spawn(process.execPath, [__filename, ...args], spawnOptions);

    child.on("error", (err) => {
      logStream.end();
      reject(new Error(`Failed to spawn background process: ${err.message}`));
    });

    child.on("spawn", () => {
      child.unref(); // Allow parent to exit
      resolve(child.pid!);
    });

    // If child exits immediately, it's an error
    child.on("exit", (code) => {
      if (code !== null && code !== 0) {
        logStream.end();
        reject(new Error(`Background process exited with code ${code}`));
      }
    });
  });
}
