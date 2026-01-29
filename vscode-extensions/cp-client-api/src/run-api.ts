import { ILogger } from "cp-client-common";
import { BaseAPI } from "./base-api";
import { APIOptions, PipelineRunModel } from "./types";

/**
 * Pipeline Run API client.
 * Corresponds to pipe-cli src/api/pipeline_run.py:PipelineRun class.
 */
export class RunAPI extends BaseAPI {
  constructor(options: APIOptions, logger: ILogger) {
    super(options, logger);
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
  static async get(runId: number, options: APIOptions, logger: ILogger): Promise<PipelineRunModel> {
    const api = new RunAPI(options, logger);
    return await api.getRun(runId);
  }

  /**
   * Check if run is initialized for SSH.
   * 
   * @param runId - Pipeline run ID
   * @param options - API options
   * @returns True if run is initialized
   */
  static async isInitialized(runId: number, options: APIOptions, logger: ILogger): Promise<boolean> {
    const api = new RunAPI(options, logger);
    return await api.isRunInitialized(runId);
  }
}
