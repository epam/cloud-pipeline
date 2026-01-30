/**
 * Network endpoint (host + port).
 * Maps to pipe-cli target_endpoint and proxy_endpoint tuples.
 * 
 * Example:
 *   targetEndpoint = { host: '10.244.78.133', port: 22 }  (maps to pipe-cli target_endpoint)
 *   proxyEndpoint = { host: 'edge.aws.cloud-pipeline.com', port: 443 }  (maps to pipe-cli proxy_endpoint)
 */
export interface Endpoint {
  host: string;
  port: number;
}

export interface ProxyEndpoint extends Endpoint {
  username?: string;
  password?: string;
}

