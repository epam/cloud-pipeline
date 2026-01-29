/**
 * Utility functions for CLI operations
 */

import { TunnelManagerConfig, parseProxyUrl } from "cp-client-tunnel";
import { ILogger } from "cp-client-common";
import { GlobalOptions } from "./types";

/**
 * Create tunnel manager configuration from CLI options
 */
export function createTunnelManagerConfig(
  opts: GlobalOptions, logger: ILogger
): TunnelManagerConfig {
  // Parse platform URL to extract host/port (allows http/https or bare host)
  const apiUrl = process.env.CP_API
    || process.env.API; // From pipe-cli

  const apiToken = process.env.CP_API_TOKEN
    || process.env.API_TOKEN; // From pipe-cli

  if (!apiUrl || !apiToken)
    throw new Error("API URL and token must be provided");

  // Proxy auth: mirror pipe-cli behavior (Basic base64 user:access_key)
  const envProxyUrl = process.env.CP_PROXY_URL
    || undefined;

  const proxy = parseProxyUrl(envProxyUrl, () => {
    return {
      username: process.env.CP_PROXY_USERNAME,
      password: process.env.CP_PROXY_PASSWORD
    }
  });

  return {
    proxy,
    connectionTimeout: 30,
    apiUrl,
    apiToken,  // Use same token for API calls
  };
}
