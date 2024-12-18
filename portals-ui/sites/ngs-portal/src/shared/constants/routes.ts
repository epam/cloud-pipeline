export enum AppRoutes {
  HOME = 'home',
  PROJECTS = 'projects',
  PROJECT = 'project',
  PIPELINES = 'pipelines',
  RUNS = 'runs',
  NOT_FOUND = 'not_found',
}

export const RoutePath: Record<AppRoutes, string> = {
  [AppRoutes.HOME]: '/',
  [AppRoutes.PROJECTS]: '/projects',
  [AppRoutes.PROJECT]: '/project',
  [AppRoutes.PIPELINES]: '/pipelines',
  [AppRoutes.RUNS]: '/runs',
  [AppRoutes.NOT_FOUND]: '*',
};
