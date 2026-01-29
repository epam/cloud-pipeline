#!/usr/bin/env node

/**
 * Cloud Pipeline CLI entry point
 */

import * as dotenv from "dotenv";
import * as path from "path";
import { existsSync, readFileSync } from "fs";
import { Command } from "commander";
import {
  tunnelListAction,
  tunnelStartAction,
  tunnelStopAction,
} from "./cli/commands";
import { ConsoleLogger, ILogger, LogLevel, LogLevelName } from "cp-client-common";

// Load environment variables from .env file if it exists
const envPath = path.resolve(process.cwd(), ".env");
if (existsSync(envPath)) {
  dotenv.config({ path: envPath });
}

// Error.stackTraceLimit = 3;

const pkg = JSON.parse(
  readFileSync(path.resolve(__dirname, "../package.json"), "utf8"),
);

/**
 * Create logger with appropriate log level based on CLI options
 */
function createLogger(opts: any): ILogger {
  // Determine log level from options
  let logLevelName: LogLevelName = "info";

  if (opts.trace) {
    logLevelName = "trace";
  } else if (opts.debug) {
    logLevelName = "debug";
  } else if (opts.logLevel) {
    const normalizedLevel = opts.logLevel.toLowerCase();
    if (normalizedLevel in LogLevel) {
      logLevelName = normalizedLevel as LogLevelName;
    }
  }

  return new ConsoleLogger(console, logLevelName);
}

const program = new Command();
program
  .description("Cloud Pipeline CLI")
  .version(pkg.version)
  .addHelpCommand(false);

// Tunnel commands
const tunnelCmd = program
  .command("tunnel")
  .description("Tunnel management commands");

// tunnel list
tunnelCmd
  .command("list")
  .description("List active tunnel connections")
  .option("-v, --log-level <level>", "Log level (ERROR, WARNING, INFO, DEBUG)")
  .option("-u, --user <user>", "User name (admin only)")
  .option("--debug", "Enable debug logging")
  .option("--trace", "Enable trace logging")
  .action((opts) => {
    const logger = createLogger(opts);
    return tunnelListAction(opts, logger);
  });

// tunnel start
tunnelCmd
  .command("start <run_id>")
  .description("Start a tunnel to a Cloud Pipeline run")
  .option("-lp, --local-port <port>", "Local port (single or range 4567-4569)")
  .option("-rp, --remote-port <port>", "Remote port (default 22)")
  .option(
    "-ct, --connection-timeout <seconds>",
    "Connection timeout in seconds",
  )
  .option("-s, --ssh", "Configure passwordless SSH")
  .option("-sp, --ssh-path <path>", "SSH config path")
  .option("-sh, --ssh-host <host>", "SSH host")
  .option("-su, --ssh-user <user>", "SSH user (can be used multiple times)")
  .option("-sk, --ssh-keep", "Keep SSH config after tunnel stops")
  .option("-d, --direct", "Direct connection without proxy")
  .option("-f, --foreground", "Run tunnel in foreground")
  .option("-ke, --keep-existing", "Skip if tunnel already exists")
  .option("-ks, --keep-same", "Skip if same config already exists")
  .option("-re, --replace-existing", "Replace existing tunnel")
  .option("-rd, --replace-different", "Replace if config differs")
  .option("--ignore-existing", "Ignore existing tunnels")
  .option("--ignore-owner", "Replace tunnels from other users")
  .option("-rg, --region <region>", "Edge region name")
  .option("-l, --log-file <file>", "Log file path")
  .option("-t, --timeout <seconds>", "Tunnel health check timeout")
  .option("-ts, --timeout-stop <seconds>", "Tunnel stop timeout")
  .option("-v, --log-level <level>", "Log level")
  .option("-u, --user <user>", "User name")
  .option("--noclean", "Disable resource cleanup")
  .option("--debug", "Enable debug logging")
  .option("--trace", "Enable trace logging")
  .action((runId, opts) => {
    const logger = createLogger(opts);
    return tunnelStartAction(runId, opts, logger);
  });

// tunnel stop
tunnelCmd
  .command("stop [run_id]")
  .description("Stop a tunnel")
  .option("-lp, --local-port <port>", "Local port to stop")
  .option("-f, --force", "Force stop (SIGKILL)")
  .option("-ts, --timeout-stop <seconds>", "Timeout for graceful stop")
  .option("-v, --log-level <level>", "Log level")
  .option("-u, --user <user>", "User name")
  .option("--debug", "Enable debug logging")
  .option("--trace", "Enable trace logging")
  .action((runId, opts) => {
    const logger = createLogger(opts);
    return tunnelStopAction(runId, opts, logger);
  });

// Parse and execute
program.parseAsync(process.argv);
