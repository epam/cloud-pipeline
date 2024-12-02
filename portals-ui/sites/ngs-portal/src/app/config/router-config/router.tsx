import type { RouteObject } from 'react-router-dom';
import { createHashRouter } from 'react-router-dom';
import { Layout } from '../../../pages/layout/index.tsx';
import { HomePage } from '../../../pages/home/home.tsx';
import Pipelines from '../../../pages/pipelines/index.tsx';
import { ProjectsPage } from '../../../pages/projects';
import Runs from '../../../pages/runs/index.tsx';
import { AppRoutes, RoutePath } from '../../../shared/constants/routes.ts';

const routerConfig: Record<AppRoutes, RouteObject> = {
  [AppRoutes.HOME]: { path: RoutePath[AppRoutes.HOME], element: <HomePage /> },
  [AppRoutes.PROJECTS]: {
    path: RoutePath[AppRoutes.PROJECTS],
    element: <ProjectsPage />,
  },
  [AppRoutes.PIPELINES]: {
    path: RoutePath[AppRoutes.PIPELINES],
    element: <Pipelines />,
  },
  [AppRoutes.RUNS]: { path: RoutePath[AppRoutes.RUNS], element: <Runs /> },
  // TODO: creat not-found page
  [AppRoutes.NOT_FOUND]: {
    path: RoutePath[AppRoutes.NOT_FOUND],
    element: <HomePage />,
  },
};

const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    children: Object.values(routerConfig),
  },
];

export const appRouter = createHashRouter(routes);
