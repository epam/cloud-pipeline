import type {
  FilterField,
  PagedRunsResult,
  PagingRunFilter,
  PagingRunFilterExpression,
  PipelineRun,
  PipelineRunFilter,
  PipelineStart,
  RunLog,
} from '../../@types/runs.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function launchRun(start: PipelineStart): Promise<PipelineRun> {
  return cloudPipelineApi.jsonPost<PipelineRun>({uri: 'run', body: start});
}

export async function launchRunConfiguration(
  configurationId: number,
  entryName: string,
  start?: Partial<PipelineStart>,
): Promise<PipelineRun> {
  return cloudPipelineApi.jsonPost<PipelineRun>({
    uri: 'runConfiguration',
    query: {configurationId, entryName},
    body: start ?? {},
  });
}

export async function loadRun(runId: number): Promise<PipelineRun> {
  return cloudPipelineApi.jsonGet<PipelineRun>({uri: `run/${runId}`});
}

export async function loadRunLogs(runId: number, from?: string): Promise<RunLog[]> {
  return cloudPipelineApi.jsonGet<RunLog[]>({uri: `run/${runId}/logs`, query: {from}});
}

export async function loadRunLogFile(runId: number): Promise<string> {
  return cloudPipelineApi.textGet({uri: `run/${runId}/logfile`});
}

export async function appendRunLog(runId: number, log: RunLog): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: `run/${runId}/log`, body: log});
}

export async function updateRunStatus(
  runId: number,
  status: Record<string, unknown>,
): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: `run/${runId}/status`, body: status});
}

export async function commitRun(runId: number, request: Record<string, unknown>): Promise<unknown> {
  return cloudPipelineApi.jsonPost({uri: `run/${runId}/commit`, body: request});
}

export async function pauseRun(runId: number): Promise<PipelineRun> {
  return cloudPipelineApi.jsonPost<PipelineRun>({uri: `run/${runId}/pause`});
}

export async function resumeRun(runId: number): Promise<PipelineRun> {
  return cloudPipelineApi.jsonPost<PipelineRun>({uri: `run/${runId}/resume`});
}

export async function terminateRun(runId: number): Promise<PipelineRun> {
  return cloudPipelineApi.jsonPost<PipelineRun>({uri: `run/${runId}/terminate`});
}

export async function filterRuns(filter: PagingRunFilter): Promise<PagedRunsResult> {
  return cloudPipelineApi.jsonPost<PagedRunsResult>({uri: 'run/filter', body: filter});
}

export async function searchRuns(filter: PagingRunFilterExpression): Promise<PagedRunsResult> {
  return cloudPipelineApi.jsonPost<PagedRunsResult>({uri: 'run/search', body: filter});
}

export async function countRuns(filter: PipelineRunFilter): Promise<number> {
  return cloudPipelineApi.jsonPost<number>({uri: 'run/count', body: filter});
}

export async function loadRunSearchKeywords(): Promise<FilterField[]> {
  return cloudPipelineApi.jsonGet<FilterField[]>({uri: 'run/search/keywords'});
}

export async function loadRuns(): Promise<PipelineRun[]> {
  return cloudPipelineApi.jsonGet<PipelineRun[]>({uri: 'runs'});
}

export async function loadRunPrice(runId: number): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: `run/${runId}/price`});
}

export async function loadRunTasks(runId: number): Promise<unknown[]> {
  return cloudPipelineApi.jsonGet({uri: `run/${runId}/tasks`});
}

export async function updateRunSids(runId: number, sids: unknown[]): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: `run/${runId}/updateSids`, body: sids});
}

export async function tagRun(runId: number, tags: Record<string, string>): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: `run/${runId}/tag`, body: tags});
}

export async function loadRunSshUrl(runId: number): Promise<string> {
  return cloudPipelineApi.jsonGet<string>({uri: `run/${runId}/ssh`});
}

export async function loadRunFsBrowserUrl(runId: number): Promise<string> {
  return cloudPipelineApi.jsonGet<string>({uri: `run/${runId}/fsbrowser`});
}

export async function loadRunActivity(): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: 'run/activity'});
}

export async function loadRunDefaultParameters(): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: 'run/defaultParameters'});
}

export async function loadRunMetrics(runId: number): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: `run/${runId}/metrics`});
}

export async function saveRunResult(runId: number, result: object): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: `run/${runId}/result`, body: result});
}

export async function loadRunResult(runId: number): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: `run/${runId}/result`});
}

export async function saveRunRuntimeData(runId: number, data: object): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: `run/${runId}/runtime/data`, body: data});
}

export async function archiveRuns(request: Record<string, unknown>): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: 'runs/archive', body: request});
}

export type {PipelineRunFilter};
