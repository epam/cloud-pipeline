import { Endpoint } from "./types";
import { ILogger } from "cp-client-common";
import { IApiOptions, Cluster, Run } from "cp-client-api";

/**
 * Run connection information.
 * Corresponds to pipe-cli run_conn_info namedtuple (ssh_operations.py:59-60).
 * 
 * Fields map to pipe-cli as follows:
 * - sshProxy: ssh_proxy tuple (proxy_host, proxy_port)
 * - sshEndpoint: ssh_endpoint tuple (pod_ip, ssh_port)
 * - sshPass: ssh_pass string (password for SSH authentication)
 * - owner: owner string (run owner username)
 * - sensitive: sensitive boolean (whether run contains sensitive data)
 * - platform: platform string (OS platform: windows/linux)
 * - parameters: parameters dict (run parameters as key-value pairs)
 */
export interface RunConnectionInfo {
  sshProxy: Endpoint;
  sshEndpoint: Endpoint;
  sshPass?: string;
  owner: string;
  sensitive: boolean;
  platform: string;
  parameters: Record<string, string>;
}

/**
 * Get connection information for a run.
 * 
 * Corresponds to pipe-cli get_conn_info() function (ssh_operations.py:260-280).
 * 
 * This function:
 * 1. Fetches run details from Cloud Pipeline API (PipelineRun.get(run_id))
 * 2. Validates run is initialized (run_model.is_initialized)
 * 3. Resolves proxy endpoint from cluster config (Cluster.get_edge_external_url)
 * 4. Returns complete connection information
 * 
 * @param runId - Pipeline run ID
 * @param region - Optional cloud region
 * @param apiUrl - Cloud Pipeline platform URL (default from env CP_PLATFORM_URL)
 * @param apiToken - API authentication token (default from env CP_API_TOKEN)
 * @param logger - Optional logger instance
 * @returns Connection information for establishing tunnel
 * @throws Error if run is not initialized or proxy URL cannot be resolved
 */
export async function getRunConnectionInfo(
  runId: number,
  region: string | undefined,
  apiOpts: IApiOptions,
  logger: ILogger,
): Promise<RunConnectionInfo> {
  logger?.debug(`getRunConnectionInfo() called for run ${runId}`);

  // Fetch run details from Cloud Pipeline API
  // Corresponds to: run_model = PipelineRun.get(run_id)
  logger?.debug(`Fetching run details for run ${runId}`);
  const run = await Run.get(runId, apiOpts, logger);

  // Check if run is initialized
  // Corresponds to: if not run_model.is_initialized
  if (run.initialized !== true) {
    throw new Error(`The specified Run ID #${runId} is not initialized for the SSH session`);
  }

  // Resolve proxy endpoint from cluster config
  // Corresponds to: proxy_url = Cluster.get_edge_external_url(region)
  logger?.debug(`Fetching edge URL for region ${region || "(default)"}`);
  const edgeProxyUrl = await Cluster.getEdgeExternalUrl(apiOpts, region, logger);

  if (!edgeProxyUrl) {
    throw new Error("Cannot retrieve EDGE service external url");
  }

  // Parse proxy URL
  // Corresponds to: proxy_url_parts = urlparse(proxy_url)
  const edgeProxyUrlParts = new URL(edgeProxyUrl.startsWith("http") ? edgeProxyUrl : `https://${edgeProxyUrl}`);
  const sshProxyHost = edgeProxyUrlParts.hostname;

  if (!sshProxyHost) {
    throw new Error(
      `Cannot resolve EDGE service hostname from its external url for the specified Run ID #${runId}`
    );
  }

  const sshProxyPort = edgeProxyUrlParts.port
    ? parseInt(edgeProxyUrlParts.port, 10)
    : edgeProxyUrlParts.protocol === "https:" ? 443 : 80;

  // Build connection info
  // Corresponds to: return run_conn_info(...)
  const connectionInfo: RunConnectionInfo = {
    sshProxy: {
      host: sshProxyHost,
      port: sshProxyPort,
    },
    sshEndpoint: {
      host: run.podIP,  // pod_ip from run model
      port: 22,         // DEFAULT_SSH_PORT
    },
    sshPass: run.sshPassword,
    owner: run.owner,
    sensitive: run.sensitive || false,
    platform: run.platform || "linux",
    parameters: run.pipelineRunParameters?.reduce(
      (acc: Record<string, string>, param: any) => {
        acc[param.name] = param.value;
        return acc;
      },
      {}
    ) || {},
  };

  logger?.debug(
    `Connection info resolved: proxy=${connectionInfo.sshProxy.host}:${connectionInfo.sshProxy.port}, ` +
    `endpoint=${connectionInfo.sshEndpoint.host}:${connectionInfo.sshEndpoint.port}`
  );

  return connectionInfo;
}

/**
 * Get connection information for a custom host (not a run).
 * 
 * Corresponds to pipe-cli get_custom_conn_info() function (ssh_operations.py:283-297).
 * 
 * @param hostId - Hostname or IP address
 * @param region - Optional cloud region
 * @param apiUrl - Cloud Pipeline platform URL
 * @param apiToken - API authentication token
 * @param logger - Optional logger instance
 * @returns Connection information for establishing tunnel
 */
export async function getCustomConnectionInfo(
  hostId: string,
  region: string | undefined,
  apiOpts: IApiOptions,
  logger: ILogger,
): Promise<RunConnectionInfo> {
  logger?.debug(`getCustomConnectionInfo() called for host ${hostId}`);

  // Resolve proxy endpoint from cluster config
  // Corresponds to: proxy_url = Cluster.get_edge_external_url(region)
  logger?.debug(`Fetching edge URL for region ${region || "(default)"}`);
  const proxyUrl = await Cluster.getEdgeExternalUrl(apiOpts, region, logger);

  if (!proxyUrl) {
    throw new Error("Cannot retrieve EDGE service external url");
  }

  // Parse proxy URL
  // Corresponds to: proxy_url_parts = urlparse(proxy_url)
  const proxyUrlParts = new URL(proxyUrl.startsWith("http") ? proxyUrl : `https://${proxyUrl}`);
  const proxyHost = proxyUrlParts.hostname;

  if (!proxyHost) {
    throw new Error("Cannot resolve EDGE service hostname from its external url");
  }

  const proxyPort = proxyUrlParts.port
    ? parseInt(proxyUrlParts.port, 10)
    : proxyUrlParts.protocol === "https:" ? 443 : 80;

  // Build connection info for custom host
  // Corresponds to: return run_conn_info(...)
  const connectionInfo: RunConnectionInfo = {
    sshProxy: {
      host: proxyHost,
      port: proxyPort,
    },
    sshEndpoint: {
      host: hostId,       // Custom host ID directly
      port: 22,           // DEFAULT_SSH_PORT
    },
    sshPass: undefined,   // No password for custom hosts
    owner: "",            // No owner for custom hosts
    sensitive: false,     // Custom hosts are not sensitive
    platform: "linux",    // Default platform
    parameters: {},       // No parameters for custom hosts
  };

  logger?.debug(
    `Connection info resolved: proxy=${connectionInfo.sshProxy.host}:${connectionInfo.sshProxy.port}, ` +
    `endpoint=${connectionInfo.sshEndpoint.host}:${connectionInfo.sshEndpoint.port}`
  );

  return connectionInfo;
}
