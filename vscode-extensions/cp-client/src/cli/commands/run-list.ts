/**
 * View runs command implementation.
 * Corresponds to pipe-cli view-runs command (pipe.py).
 */

import Table from "cli-table3";
import chalk from "chalk";
import { ILogger } from "cp-client-common";
import { PipelineRunModel, RunAPI, RunFilterOptions } from "cp-client-api";
import { GlobalCommandOptions } from "../types";
import { ApiOptions } from "../options/api-options";

/**
 * Options for run-list command.
 * Corresponds to pipe-cli view-runs command options.
 */
export interface RunListCommandOptions extends GlobalCommandOptions {
  /** Run ID to view details */
  status?: string;
  /** Filter by start date from */
  dateFrom?: string;
  /** Filter by end date to */
  dateTo?: string;
  /** Filter by pipeline */
  pipeline?: string;
  /** Filter by parent run ID */
  parentId?: number;
  /** Search runs with substring in parameters */
  find?: string;
  /** Display top N records */
  top?: number;
  /** Filter by users (comma-separated) */
  userFilter?: string;
  /** Display node details (for single run view) */
  nodeDetails?: boolean;
  /** Display parameters details (for single run view) */
  parametersDetails?: boolean;
  /** Display tasks details (for single run view) */
  tasksDetails?: boolean;
  /** Display tags details (for single run view) */
  tagsDetails?: boolean;
}

/**
 * View runs command action.
 * If runId is provided, displays details of a specific run.
 * Otherwise, displays a list of runs matching the filter criteria.
 */
export async function runListAction(
  runId: string | undefined,
  opts: RunListCommandOptions,
  logger: ILogger
): Promise<void> {
  try {
    const apiOptions = ApiOptions.fromCommandOptions(opts);

    const api = new RunAPI(apiOptions, logger);

    // If run ID is specified - display details of a specific run
    if (runId) {
      await runList(api, parseInt(runId), opts, logger);
    } else {
      // Otherwise - list runs according to filter options
      await runListAll(api, opts, logger);
    }
  } catch (err) {
    console.error("Error viewing runs:", err);
    if (opts.trace) {
      console.error((err as Error).stack);
    }
    process.exit(1);
  }
}

/**
 * Display details of a specific run.
 * Corresponds to pipe-cli view_run() function.
 * Output
 * ID:                     85984
 * Pipeline:               rockylinux:sge
 * Version:                None
 * Owner:                  ATANAS
 * Scheduled:              2026-01-16 14:15:46
 * Started:                2026-01-16 14:15:58
 * Completed               N/A
 * Status:                 RUNNING
 * ParentID:               None
 * Estimated price:        N/A
 * Tags:                   LONG_RUNNING
 */
async function runList(
  api: RunAPI,
  runId: number,
  opts: RunListCommandOptions,
  logger: ILogger
): Promise<void> {
  const run = await api.getRun(runId);

  const tag_list: string[] = Object.entries(run.tags)
    .filter(([_k, v]) => v == 'true')
    .map(([k, _v]) => k);

  console.log(`ID:                     ${run.id}`);
  console.log(`Pipeline:               ${getPipelineName(run)}`);
  console.log(`Version:                ${run.version || 'None'}`);
  console.log(`Owner:                  ${run.owner}`);
  console.log(`Scheduled:              ${run.scheduledDate || 'N/A'}`)
  console.log(`Started:                ${run.startDate || 'N/A'}`);
  console.log(`Completed:              ${run.endDate || 'N/A'}`);
  console.log(`Status:                 ${colorStatus(run.status)}`);
  console.log(`ParentID:               ${run.parentId || 'None'}`);
  console.log(`Estimated price:        ${run.totalPrice || 'N/A'}`);
  console.log(`Tags:                   ${tag_list ? tag_list.join(', ') : 'N/A'}`);

  if (run.parentId) {
    logger.debug(`Parent ID:  ${run.parentId}`);
  }

  // Service URLs (endpoints)
  if (run.serviceUrl && Object.keys(run.serviceUrl).length > 0) {
    logger.debug("\nEndpoints:");
    for (const [name, url] of Object.entries(run.serviceUrl)) {
      logger.debug(`  ${name}: ${url}`);
    }
  }

  // Parameters
  if (opts.parametersDetails) {
    logger.debug("\n" + "-".repeat(80));
    logger.debug("Parameters:");
    logger.debug("-".repeat(80));
    if (run.pipelineRunParameters && run.pipelineRunParameters.length > 0) {
      for (const param of run.pipelineRunParameters) {
        logger.debug(`${param.name} = ${param.value}`);
      }
    } else {
      logger.debug("No parameters configured");
    }
  }

  // Additional details can be added here when needed
  // (nodeDetails, tasksDetails, tagsDetails)

  logger.debug("=".repeat(80) + "\n");
}

/**
 * Display list of pipeline runs.
 * Corresponds to pipe-cli view_all_runs() function.
 */
async function runListAll(
  api: RunAPI,
  opts: RunListCommandOptions,
  logger: ILogger
): Promise<void> {
  // Parse statuses
  const statuses: string[] = [];
  if (opts.status) {
    if (opts.status.toUpperCase() !== 'ANY') {
      statuses.push(...opts.status.split(',').map(s => s.trim().toUpperCase()));
    }
  } else {
    // Default to RUNNING if no status specified
    statuses.push('RUNNING');
  }

  // Parse owners
  const owners = opts.userFilter
    ? opts.userFilter.split(',').map(u => u.trim())
    : undefined;

  // Build filter options
  const filterOptions: RunFilterOptions = {
    page: 1,
    pageSize: opts.top || 100,
    statuses: statuses.length > 0 ? statuses : undefined,
    startDateFrom: opts.dateFrom,
    endDateTo: opts.dateTo,
    parentId: opts.parentId,
    partialParameters: opts.find,
    owners,
  };

  // Parse pipeline if provided
  if (opts.pipeline) {
    // Pipeline can be specified as <name>@<version> or just <name>
    // For now, we'll just pass it as a search parameter
    // In a full implementation, we would need to resolve pipeline name to ID
    logger.warn('Pipeline filtering by name is not fully implemented yet');
  }

  const result = await api.listRuns(filterOptions);

  if (result.totalCount === 0) {
    logger.debug("No data is available for the request");
    return;
  }

  if (result.totalCount > result.pageSize) {
    logger.debug(`\nShowing ${result.pageSize} results from ${result.totalCount}:\n`);
  }

  // Create table
  const table = new Table({
    head: [
      "RunID",
      "Parent RunID",
      "Pipeline",
      "Version",
      "Status",
      "Started",
      "Owner"
    ],
    wordWrap: false,
    colAligns: ["right", "right", "right", "right", "left", "left", "left"],
    style: {
      head: [],
      border: ["cyan"],
      compact: true
    }
  });


  // Add rows
  for (const run of result.elements) {
    table.push([
      run.id.toString(),
      run.parentId?.toString() || "None",
      getPipelineName(run),
      run.version || "None",
      colorStatus(run.status),
      run.startDate || "N/A",
      run.owner
    ]);
  }

  console.log("\n" + table.toString() + "\n");
}

function getPipelineName(run: PipelineRunModel): string {
  if (run.pipelineName) return run.pipelineName;
  if (run.dockerImage) {
    return run.dockerImage.split('/').slice(-1)?.[0] || "";
  }
  return 'CMD';
}

function colorStatus(status: string): string {
  switch (status) {
    case "RUNNING":
      return chalk.green(status);
    case "SCHEDULED":
      return chalk.greenBright(status);
    default:
      return status;
  }
}
