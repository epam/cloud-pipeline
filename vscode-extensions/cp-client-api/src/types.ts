/**
 * Cloud Pipeline API types and interfaces.
 * Corresponds to pipe-cli model classes.
 */

/**
 * API client configuration options.
 */
export interface APIOptions {
  /** Cloud Pipeline platform URL (e.g., "https://aws.cloud-pipeline.com") */
  apiUrl?: string;
  /** API authentication token */
  apiToken?: string;
}

/**
 * API response wrapper.
 * Corresponds to pipe-cli API response structure.
 */
export interface APIResponse<T> {
  /** Response status */
  status: string;
  /** Response payload */
  payload?: T;
  /** Error message if status is not OK */
  message?: string;
}

/**
 * Pipeline run parameter.
 */
export interface RunParameter {
  name: string;
  value: string;
  type?: string;
  required?: boolean;
}

/**
 * Pipeline run model.
 * Corresponds to pipe-cli PipelineRunModel (src/model/pipeline_run_model.py).
 */
export interface PipelineRunModel {
  /** Run ID */
  id: number;
  /** Run status */
  status: string;
  /** Run owner */
  owner: string;
  /** Pod IP address */
  podIP: string;
  /** SSH password */
  sshPassword?: string;
  /** Whether run is initialized for SSH */
  initialized: boolean;
  /** Whether run contains sensitive data */
  sensitive: boolean;
  /** Platform (OS) */
  platform: string;
  /** Run parameters */
  pipelineRunParameters?: RunParameter[];
  /** Additional fields */
  [key: string]: any;
}

/**
 * Cluster node model.
 * Corresponds to pipe-cli ClusterNodeModel (src/model/cluster_node_model.py).
 */
export interface ClusterNodeModel {
  name: string;
  pipelineRun?: PipelineRunModel;
  created?: string;
  labels?: Record<string, string>;
  [key: string]: any;
}

/**
 * Instance type model.
 * Corresponds to pipe-cli ClusterInstanceTypeModel (src/model/cluster_instance_type_model.py).
 */
export interface ClusterInstanceTypeModel {
  name: string;
  vcpu: number;
  gpu?: number;
  memory?: number;
  [key: string]: any;
}
