export enum AppRoutes {
  HOME = 'home',
  PROJECTS = 'projects',
  PROJECT = 'project',
  PIPELINES = 'pipelines',
  PIPELINE = 'pipeline',
  RUNS = 'runs',
  LAUNCH = 'launch',
  NOT_FOUND = 'not_found',
}

export const RoutePath: Record<AppRoutes, string> = {
  [AppRoutes.HOME]: '/',
  [AppRoutes.PROJECTS]: '/projects',
  [AppRoutes.PROJECT]: '/projects/:projectId/:tabId?',
  [AppRoutes.PIPELINES]: '/pipelines',
  [AppRoutes.PIPELINE]: '/pipelines/:pipelineId',
  [AppRoutes.RUNS]: '/runs',
  [AppRoutes.LAUNCH]: '/launch/:pipelineId',
  [AppRoutes.NOT_FOUND]: '*',
};

export enum ProjectTabs {
  Info = 'info',
  Storage = 'storage',
  Pipelines = 'pipelines',
  History = 'history',
}

export function generateProjectRoutePath(
  projectId: string | number,
  tabId?: ProjectTabs,
): string {
  const tabPath = tabId ? `/${tabId}` : '';

  return `/projects/${projectId}${tabPath}`;
}

export function generatePipelineRoutePath(pipelineId: string | number): string {
  return `/pipelines/${pipelineId}`;
}

export function generateLaunchRoutePath(pipelineId: string | number): string {
  return `/launch/${pipelineId}`;
}
