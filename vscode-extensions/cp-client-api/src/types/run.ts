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
 * Pipeline run filter options.
 * Corresponds to pipe-cli PipelineRun.list() parameters.
 */
export interface RunFilterOptions {
  /** Page number (1-based) */
  page?: number;
  /** Page size */
  pageSize?: number;
  /** Run statuses to filter by */
  statuses?: string[];
  /** Filter runs started after this date */
  startDateFrom?: string;
  /** Filter runs ended before this date */
  endDateTo?: string;
  /** Filter by pipeline IDs */
  pipelineIds?: number[];
  /** Filter by pipeline versions */
  versions?: string[];
  /** Filter by parent run ID */
  parentId?: number;
  /** Filter by partial parameter match */
  partialParameters?: string;
  /** Filter by owners */
  owners?: string[];
}
