/**
 * Pipeline run types.
 */

import { RunParameter } from "../run";

/**
 * Common run info contract for both CLI (pipe view-runs) and API (run/filter) run lists.
 */
export interface IRunInfo {
  /** Run ID */
  id: number;
  /** Parent run ID, or null if none */
  parentId: number | null;
  /** Pipeline name */
  pipelineName: string;
  /** Pipeline version, or null if none */
  version: string | null;
  /** Run status */
  status: string;
  /** Run start date */
  startDate: string;
  /** Run owner */
  owner: string;
}

/**
 * Pipeline run model.
 * Corresponds to pipe-cli PipelineRunModel (src/model/pipeline_run_model.py).
 * Extends IRunInfo with API-specific fields.
 */
export interface PipelineRunModel extends IRunInfo {
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
  /** Docker image */
  dockerImage?: string;
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
