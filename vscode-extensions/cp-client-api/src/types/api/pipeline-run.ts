/**
 * Pipeline run types.
 */

import { RunParameter } from "../run";

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
  /** Pipeline ID */
  pipelineId?: number;
  /** Pipeline name */
  pipelineName?: string;
  /** Pipeline version */
  version?: string;
  /** Docker image */
  dockerImage?: string;
  /** Parent run ID */
  parentId?: number;
  /** Run start date */
  startDate?: string;
  /** Run end date */
  endDate?: string;
  /** Service URLs */
  serviceUrl?: Record<string, string>;
  /** Additional fields */
  [key: string]: any;
}

/**
 * Pipeline run filter result.
 * Corresponds to pipe-cli PipelineRunFilterModel (src/model/pipeline_run_filter_model.py).
 */
export interface PipelineRunFilterModel {
  /** Current page number */
  page: number;
  /** Page size */
  pageSize: number;
  /** Total count of matching runs */
  totalCount: number;
  /** List of runs */
  elements: PipelineRunModel[];
}
