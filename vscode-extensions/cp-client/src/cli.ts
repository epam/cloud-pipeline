#!/usr/bin/env node

/**
 * Cloud Pipeline CLI entry point
 */

import * as dotenv from "dotenv";
import * as path from "path";
import { existsSync, readFileSync } from "fs";
import {
  tunnelListAction,
  tunnelStartAction,
  tunnelStopAction,
  runListAction,
} from "./cli/commands";
import { ConsoleLogger, ILogger, LogLevel, LogLevelName } from "cp-client-common";
import { PipeCommand } from "./cli-pipe-command";
import { RunListCommandOptions } from "./cli/commands/run-list";

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

const program = new PipeCommand();
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
  .addTunnelListOptions()
  .action((opts: any) => {
    const logger = createLogger(opts);
    return tunnelListAction(opts, logger);
  });

// tunnel start
tunnelCmd
  .command("start <run_id>")
  .description("Start a tunnel to a Cloud Pipeline run")
  .addTunnelStartOptions()
  .action((runId: any, opts: any) => {
    const logger = createLogger(opts);
    return tunnelStartAction(runId, opts, logger);
  });

// tunnel stop
tunnelCmd
  .command("stop [run_id]")
  .description("Stop a tunnel")
  .addTunnelStopOptions()
  .action((runId: any, opts: any) => {
    const logger = createLogger(opts);
    return tunnelStopAction(runId, opts, logger);
  });

// view-runs command
program
  .command("view-runs [run_id]")
  .description("Display details of a run or list of pipeline runs")
  .addRunListOptions()
  .action((runId: string | undefined, opts: RunListCommandOptions) => {
    const logger = createLogger(opts);
    return runListAction(runId, opts, logger);
  });

// Run commands group
const runCmd = program
  .command("run")
  .description("Pipeline run management commands");

// run list (synonym for view-runs)
runCmd
  .command("list [run_id]")
  .description("Display details of a run or list of pipeline runs")
  .addRunListOptions()
  .action((runId: string | undefined, cmdOpts: RunListCommandOptions) => {
    const logger = createLogger(cmdOpts);
    return runListAction(runId, cmdOpts, logger);
  });

// Parse and execute
program.parseAsync(process.argv);
