/**
 * Cluster types.
 */

import { PipelineRunModel } from "./pipeline-run";

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
