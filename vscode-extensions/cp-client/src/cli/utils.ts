/**
 * Utility functions for CLI operations
 */

import { TunnelManagerConfig } from "cp-client-tunnel";
import { FileLogger, ILogger } from "cp-client-common";
import * as path from "path";
import * as os from "os";
import { GlobalOptions } from "./types";

/**
 * Create tunnel manager configuration from CLI options
 */
export function createTunnelManagerConfig(
  opts: GlobalOptions, logger: ILogger
): TunnelManagerConfig {
  const logLevel = opts.debug ? "debug" : opts.trace ? "trace" : "info";
  const logDir = path.join(os.homedir(), ".pipe", "logs");
  const logFile = path.join(logDir, "tunnel.log");

  return {
    proxyHost: process.env.CP_PLATFORM_URL || "aws.cloud-pipeline.com",
    proxyPort: 443,
    connectionTimeout: 30,
    logger: new FileLogger(logFile, logLevel as any, undefined, logger),
  };
}
