import type { RouteObject } from 'react-router-dom';
import { createHashRouter } from 'react-router-dom';
import { Layout } from '../../../pages/layout';
import { HomePage } from '../../../pages/home';
import { PipelinesPage } from '../../../pages/pipelines';
import { ProjectPage } from '../../../pages/project';
import { AppRoutes, RoutePath } from '../../../shared/constants/routes.ts';
import { RunsPage } from '../../../pages/runs';
import { ProjectsPage } from '../../../pages/projects';
import { PipelinePage } from '../../../pages/pipeline';
import { LaunchPage } from '../../../pages/launch';

export type NgsPortalRoute = RouteObject & {
  ngsPortalRoute: AppRoutes;
};

function generateNgsPortalRoute(
  appRoute: AppRoutes,
  routeConfig: Omit<NgsPortalRoute, 'ngsPortalRoute' | 'path'>,
): NgsPortalRoute {
  return {
    ...routeConfig,
    ngsPortalRoute: appRoute,
    path: RoutePath[appRoute],
  } as NgsPortalRoute;
}

export const routes: NgsPortalRoute[] = [
  generateNgsPortalRoute(AppRoutes.HOME, { element: <HomePage /> }),
  generateNgsPortalRoute(AppRoutes.PROJECTS, { element: <ProjectsPage /> }),
  generateNgsPortalRoute(AppRoutes.PROJECT, { element: <ProjectPage /> }),
  generateNgsPortalRoute(AppRoutes.PIPELINES, { element: <PipelinesPage /> }),
  generateNgsPortalRoute(AppRoutes.PIPELINE, { element: <PipelinePage /> }),
  generateNgsPortalRoute(AppRoutes.RUNS, { element: <RunsPage /> }),
  generateNgsPortalRoute(AppRoutes.LAUNCH, { element: <LaunchPage /> }),
  // TODO: create not-found page
  generateNgsPortalRoute(AppRoutes.NOT_FOUND, { element: <HomePage /> }),
];

const rootRoute: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    children: routes,
  },
];

export const appRouter = createHashRouter(rootRoute);
