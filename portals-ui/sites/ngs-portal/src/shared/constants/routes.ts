export enum AppRoutes {
  HOME = 'home',
  PROJECTS = 'projects',
  PROJECT = 'project',
  PROJECT = 'project',
  PIPELINES = 'pipelines',
  PIPELINE = 'pipeline',
  RUNS = 'runs',
  NOT_FOUND = 'not_found',
}

export const RoutePath: Record<AppRoutes, string> = {
  [AppRoutes.HOME]: '/',
  [AppRoutes.PROJECTS]: '/projects',
  [AppRoutes.PROJECT]: '/project/:projectId',
  [AppRoutes.PIPELINES]: '/pipelines',
  [AppRoutes.PIPELINE]: '/pipeline/:pipelineId',
  [AppRoutes.RUNS]: '/runs',
  [AppRoutes.NOT_FOUND]: '*',
};
