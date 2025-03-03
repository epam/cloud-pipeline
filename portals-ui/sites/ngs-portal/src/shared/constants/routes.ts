import { LaunchFormSearchParams } from './search-params';

export enum AppRoutes {
  HOME = 'home',
  PROJECTS = 'projects',
  PROJECT = 'project',
  PIPELINES = 'pipelines',
  PIPELINE = 'pipeline',
  RUNS = 'runs',
  RUN = 'run',
  LAUNCH = 'launch',
  NOT_FOUND = 'not_found',
}

export const RoutePath: Record<AppRoutes, string> = {
  [AppRoutes.HOME]: '/',
  [AppRoutes.PROJECTS]: '/projects',
  [AppRoutes.PROJECT]: '/projects/:projectId/:tabId?',
  [AppRoutes.PIPELINES]: '/pipelines',
  [AppRoutes.PIPELINE]: '/pipelines/:pipelineId/:tabId?',
  [AppRoutes.RUNS]: '/runs',
  [AppRoutes.RUN]: '/runs/:runId/:tabId?',
  [AppRoutes.LAUNCH]: '/launch',
  [AppRoutes.NOT_FOUND]: '*',
};

export enum ProjectTabs {
  Info = 'info',
  Storage = 'storage',
  Pipelines = 'pipelines',
  History = 'history',
}

export enum PipelineTabs {
  Documents = 'documents',
  Code = 'code',
  Configuration = 'configuration',
  RunHistory = 'run-history',
}

export enum RunLogsTabs {
  Logs = 'logs',
  Parameters = 'parameters',
  Tasks = 'tasks',
}

export function generateProjectRoutePath(projectId: string | number, tabId?: ProjectTabs): string {
  const tabPath = tabId ? `/${tabId}` : '';

  return `/projects/${projectId}${tabPath}`;
}

export function generatePipelineRoutePath(pipelineId: string | number, tabId?: PipelineTabs): string {
  const tabPath = tabId ? `/${tabId}` : '';

  return `/pipelines/${pipelineId}${tabPath}`;
}

export function generateRunLogsRoutePath(runId: string | number, tabId?: RunLogsTabs): string {
  const tabPath = tabId ? `/${tabId}` : '';
  return `/runs/${runId}${tabPath}`;
}

export function generateLaunchRoutePath(
  pipelineId?: string | number,
  runId?: string | number,
  version?: string,
): string {
  const query = new URLSearchParams();

  if (version) {
    query.append(LaunchFormSearchParams.Version, version);
  }

  if (runId) {
    query.append(LaunchFormSearchParams.RunId, `${runId}`);
  } else if (pipelineId) {
    query.append(LaunchFormSearchParams.PipelineId, `${pipelineId}`);
  }

  const queryString = query.toString();
  return queryString ? `/launch?${queryString}` : '/launch';
}
