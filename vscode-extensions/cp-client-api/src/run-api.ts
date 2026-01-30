import { ILogger } from "cp-client-common";
import { BaseAPI } from "./base-api";
import { IApiOptions, PipelineRunFilterModel, PipelineRunModel } from "./types/api";
import { RunFilterOptions } from "./types/run";

/**
 * Pipeline Run API client.
 * Corresponds to pipe-cli src/api/pipeline_run.py:PipelineRun class.
 */
export class RunAPI extends BaseAPI {
  constructor(apiOpts: IApiOptions, logger: ILogger) {
    super(apiOpts, logger);
  }

  /**
   * Get pipeline run by ID.
   * Corresponds to pipe-cli PipelineRun.get(run_id).
   * 
   * @param runId - Pipeline run ID
   * @returns Pipeline run model
   * @throws Error if run not found
   */
  async getRun(runId: number): Promise<PipelineRunModel> {
    this.logger?.debug(`Getting pipeline run: ${runId}`);
    return await this.call<PipelineRunModel>(`run/${runId}`);
  }

  /**
   * List pipeline runs with filtering.
   * Corresponds to pipe-cli PipelineRun.list().
   * 
   * @param options - Filter options
   * @returns Pipeline run filter result
   */
  async listRuns(options: RunFilterOptions = {}): Promise<PipelineRunFilterModel> {
    this.logger?.debug(`Listing pipeline runs with options:`, options);

    const data: any = {
      page: options.page ?? 1,
      pageSize: options.pageSize ?? 100,
    };

    if (options.statuses && options.statuses.length > 0) {
      data.statuses = options.statuses;
    }
    if (options.startDateFrom) {
      data.startDateFrom = options.startDateFrom;
    }
    if (options.endDateTo) {
      data.endDateTo = options.endDateTo;
    }
    if (options.pipelineIds && options.pipelineIds.length > 0) {
      data.pipelineIds = options.pipelineIds;
    }
    if (options.versions && options.versions.length > 0) {
      data.versions = options.versions;
    }
    if (options.parentId !== undefined) {
      data.parentId = options.parentId;
    }
    if (options.partialParameters) {
      data.partialParameters = options.partialParameters;
    }
    if (options.owners && options.owners.length > 0) {
      data.owners = options.owners;
    }

    const result = await this.call<PipelineRunFilterModel>('run/filter', 'POST', data);

    // Ensure page and pageSize are set in the result
    result.page = data.page;
    result.pageSize = data.pageSize;

    return result;
  }

  /**
   * Check if run is initialized for SSH.
   * Corresponds to pipe-cli run_model.is_initialized check.
   * 
   * @param runId - Pipeline run ID
   * @returns True if run is initialized
   */
  async isRunInitialized(runId: number): Promise<boolean> {
    const run = await this.getRun(runId);
    return run.initialized === true;
  }
}

/**
 * Static helper methods for Run API.
 */
export class Run {
  /**
   * Get pipeline run by ID.
   * Static convenience method.
   * 
   * @param runId - Pipeline run ID
   * @param options - API options
   * @returns Pipeline run model
   */
  static async get(runId: number, apiOpts: IApiOptions, logger: ILogger): Promise<PipelineRunModel> {
    const api = new RunAPI(apiOpts, logger);
    return await api.getRun(runId);
  }

  /**
   * List pipeline runs with filtering.
   * Static convenience method.
   * Corresponds to pipe-cli PipelineRun.list().
   * 
   * @param filterOptions - Filter options
   * @param options - API options
   * @param logger - Logger instance
   * @returns Pipeline run filter result
   */
  static async list(
    filterOptions: RunFilterOptions,
    apiOpts: IApiOptions,
    logger: ILogger
  ): Promise<PipelineRunFilterModel> {
    const api = new RunAPI(apiOpts, logger);
    return await api.listRuns(filterOptions);
  }

  /**
   * Check if run is initialized for SSH.
   * 
   * @param runId - Pipeline run ID
   * @param apiOpts - API options
   * @returns True if run is initialized
   */
  static async isInitialized(runId: number, apiOpts: IApiOptions, logger: ILogger): Promise<boolean> {
    const api = new RunAPI(apiOpts, logger);
    return await api.isRunInitialized(runId);
  }
}
