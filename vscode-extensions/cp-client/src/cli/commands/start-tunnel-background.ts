import { ILogger } from "cp-client-common";
import * as os from "os";
import * as path from "path";
import * as net from "net";
import { spawn } from "child_process";
import { createWriteStream, mkdirSync } from "fs";
import { TunnelStartCommandOptions } from "../types";

/**
 * Check if a port is being listened on
 */
async function isPortListening(port: number, logger: ILogger): Promise<boolean> {
    logger.trace(`Checking if port ${port} is listening...`);
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
    }).then((res) => {
        logger.trace(`Port ${port} listening status: ${res}`);
        return <boolean>res;
    });
}

/**
 * Wait for background tunnel to be ready
 */
async function waitForBackgroundTunnel(
    childPid: number,
    localPort: number,
    timeoutSeconds: number,
    logger: ILogger,
): Promise<void> {
    const pollingDelay = 1000; // 1 second
    const attempts = timeoutSeconds;

    logger.info(
        `Waiting for tunnel process #${childPid} to listen on port ${localPort}...`,
    );

    for (let i = 0; i < attempts; i++) {
        logger.trace(`Attempt ${i + 1}/${attempts}: Starting health check cycle`);

        logger.trace(`Waiting ${pollingDelay}ms before next check...`);
        await new Promise((resolve) => setTimeout(resolve, pollingDelay));
        logger.trace(`Wait completed, proceeding with checks`);

        // Check if process is still alive
        logger.trace(`Checking if process #${childPid} is still alive...`);
        try {
            process.kill(childPid, 0); // Signal 0 checks if process exists
            logger.trace(`Process #${childPid} is alive`);
        } catch (err) {
            const reason = err instanceof Error ? err.message : String(err);
            logger.trace(`Process #${childPid} check failed: ${reason}`);
            throw new Error(
                `Background tunnel process #${childPid} has exited unexpectedly: ${reason}`,
            );
        }

        // Check if port is listening
        logger.trace(`Checking if port ${localPort} is listening...`);
        const isListening = await isPortListening(localPort, logger);
        logger.trace(`Port ${localPort} listening status: ${isListening}`);

        if (isListening) {
            logger.trace(`Port is ready, tunnel initialization successful`);
            logger.info("Background tunnel is initialized.");
            return;
        }

        logger.trace(`Port not ready yet, will retry...`);
    }

    logger.trace(`All ${attempts} attempts exhausted, tunnel failed to initialize`);
    throw new Error(
        `Failed to serve tunnel in background. Tunnel is not initialized after ${timeoutSeconds} seconds.`,
    );
}

/**
 * Start tunnel in background by spawning detached process
 */
function startBackgroundTunnel(
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

        const timeoutTimer = setTimeout(() => {
            logStream.end();
            reject(new Error(`Background process did not start within ${timeout} seconds`));
        }, timeout * 1000);

        child.on("error", (err) => {
            clearTimeout(timeoutTimer);
            logStream.end();
            reject(new Error(`Failed to spawn background process: ${err.message}`));
        });

        child.on("spawn", () => {
            clearTimeout(timeoutTimer);
            child.unref(); // Allow parent to exit
            resolve(child.pid!);
        });

        // If child exits immediately, it's an error
        child.on("exit", (code) => {
            clearTimeout(timeoutTimer);
            if (code !== null && code !== 0) {
                logStream.end();
                reject(new Error(`Background process exited with code ${code}`));
            }
        });
    });
}

export async function startTunnelBackground(
    runId: number,
    cmdOptions: TunnelStartCommandOptions,
    config: any,
    logger: ILogger
): Promise<void> {
    if (!cmdOptions.localPort)
        throw new Error("Local port is required");

    const logFile =
        cmdOptions.logFile ||
        path.join(os.homedir(), ".pipe", "logs", `tunnel-${runId}.log`);
    const healthCheckTimeout = cmdOptions.timeout ? parseInt(cmdOptions.timeout) : 300; // 5 minutes default

    logger.info(`Launching background tunnel to run ${runId}...`);

    const childPid = await startBackgroundTunnel(logFile, healthCheckTimeout);

    const localPort = parseInt(cmdOptions.localPort);

    await waitForBackgroundTunnel(childPid, localPort, healthCheckTimeout, logger);

    logger.info(`Tunnel started to run ${runId}`);
    logger.info(
        `Local port: ${cmdOptions.localPort}, Remote port: ${cmdOptions.remotePort}`,
    );
    logger.info(`Background process PID: ${childPid}`);
    logger.info(`Logs: ${logFile}`);
}
