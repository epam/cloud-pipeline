export enum AppRoutes {
  HOME = 'home',
  PROJECTS = 'projects',
  PROJECT = 'project',
  PIPELINES = 'pipelines',
  PIPELINE = 'pipeline',
  RUNS = 'runs',
  NOT_FOUND = 'not_found',
}

export const RoutePath: Record<AppRoutes, string> = {
  [AppRoutes.HOME]: '/',
  [AppRoutes.PROJECTS]: '/projects',
  [AppRoutes.PROJECT]: '/projects/:projectId',
  [AppRoutes.PIPELINES]: '/pipelines',
  [AppRoutes.PIPELINE]: '/pipelines/:pipelineId',
  [AppRoutes.RUNS]: '/runs',
  [AppRoutes.NOT_FOUND]: '*',
};

export function generateProjectRoutePath(projectId: string | number): string {
  return `/projects/${projectId}`;
}

export function generatePipelineRoutePath(pipelineId: string | number): string {
  return `/pipelines/${pipelineId}`;
}
