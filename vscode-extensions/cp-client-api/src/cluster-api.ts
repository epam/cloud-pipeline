import { ILogger } from "cp-client-common";
import { BaseAPI } from "./base-api";
import { APIOptions, ClusterNodeModel, ClusterInstanceTypeModel } from "./types";

/**
 * Cluster API client.
 * Corresponds to pipe-cli src/api/cluster.py:Cluster class.
 */
export class ClusterAPI extends BaseAPI {
  constructor(options: APIOptions, logger: ILogger) {
    super(options, logger);
  }

  /**
   * Get EDGE service external URL.
   * Corresponds to pipe-cli Cluster.get_edge_external_url(region).
   * 
   * This URL is used as proxy endpoint for tunnel connections.
   * 
   * @param region - Optional cloud region identifier
   * @returns External URL string (e.g., "https://edge.aws.cloud-pipeline.com")
   * @throws Error if URL cannot be retrieved
   */
  async getEdgeExternalUrl(region?: string): Promise<string> {
    this.logger.debug(`Getting edge external URL for region: ${region || "(default)"}`);

    let path = "cluster/edge/externalUrl";
    if (region) {
      path += `?region=${encodeURIComponent(region)}`;
    }

    const url = await this.call<string>(path);

    if (!url) {
      throw new Error("Cannot retrieve EDGE service external URL");
    }

    this.logger?.debug(`Edge external URL: ${url}`);
    return url;
  }

  /**
   * List all cluster nodes.
   * Corresponds to pipe-cli Cluster.list().
   * 
   * @returns Array of cluster nodes
   */
  async listNodes(): Promise<ClusterNodeModel[]> {
    this.logger?.debug("Listing cluster nodes");
    return await this.call<ClusterNodeModel[]>("cluster/node/loadAll");
  }

  /**
   * Get cluster node by name.
   * Corresponds to pipe-cli Cluster.get(name).
   * 
   * @param name - Node name
   * @returns Cluster node
   */
  async getNode(name: string): Promise<ClusterNodeModel> {
    this.logger?.debug(`Getting cluster node: ${name}`);
    return await this.call<ClusterNodeModel>(`cluster/node/${encodeURIComponent(name)}/load`);
  }

  /**
   * Terminate cluster node.
   * Corresponds to pipe-cli Cluster.terminate_node(name).
   * 
   * @param name - Node name
   * @returns Terminated node
   */
  async terminateNode(name: string): Promise<ClusterNodeModel> {
    this.logger?.debug(`Terminating cluster node: ${name}`);
    return await this.call<ClusterNodeModel>(`cluster/node/${encodeURIComponent(name)}`, "DELETE");
  }

  /**
   * List available instance types.
   * Corresponds to pipe-cli Cluster.list_instance_types().
   * 
   * @returns Array of instance types
   */
  async listInstanceTypes(): Promise<ClusterInstanceTypeModel[]> {
    this.logger?.debug("Listing instance types");
    return await this.call<ClusterInstanceTypeModel[]>("cluster/instance/loadAll");
  }
}

/**
 * Static helper methods for Cluster API.
 * Provides convenience methods similar to pipe-cli class methods.
 */
export class Cluster {
  /**
   * Get EDGE service external URL.
   * Static convenience method that creates API instance internally.
   * 
   * @param options - API options (platformUrl, apiToken, region, logger)
   * @returns External URL string
   */
  static async getEdgeExternalUrl(options: APIOptions, region: string | undefined, logger: ILogger): Promise<string> {
    const api = new ClusterAPI(options, logger);
    return await api.getEdgeExternalUrl(region);
  }

  /**
   * List all cluster nodes.
   * 
   * @param options - API options
   * @returns Array of cluster nodes
   */
  static async listNodes(options: APIOptions, logger: ILogger): Promise<ClusterNodeModel[]> {
    const api = new ClusterAPI(options, logger);
    return await api.listNodes();
  }

  /**
   * Get cluster node by name.
   * 
   * @param name - Node name
   * @param options - API options
   * @returns Cluster node
   */
  static async getNode(name: string, options: APIOptions, logger: ILogger): Promise<ClusterNodeModel> {
    const api = new ClusterAPI(options, logger);
    return await api.getNode(name);
  }

  /**
   * List available instance types.
   * 
   * @param options - API options
   * @returns Array of instance types
   */
  static async listInstanceTypes(options: APIOptions, logger: ILogger): Promise<ClusterInstanceTypeModel[]> {
    const api = new ClusterAPI(options, logger);
    return await api.listInstanceTypes();
  }
}
