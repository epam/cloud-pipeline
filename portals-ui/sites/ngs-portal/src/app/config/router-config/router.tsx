import type { RouteObject } from 'react-router-dom';
import { createHashRouter } from 'react-router-dom';
import { Layout } from '../../../pages/layout/index.tsx';
import { Home } from '../../../pages/home/home.tsx';
import Pipelines from '../../../pages/pipelines/index.tsx';
import Projects from '../../../pages/projects/index.tsx';
import Runs from '../../../pages/runs/index.tsx';
import { AppRoutes, RoutePath } from '../../../shared/constants/routes.ts';

const routerConfig: Record<AppRoutes, RouteObject> = {
  [AppRoutes.HOME]: { path: RoutePath[AppRoutes.HOME], element: <Home /> },
  [AppRoutes.PROJECTS]: {
    path: RoutePath[AppRoutes.PROJECTS],
    element: <Projects />,
  },
  [AppRoutes.PIPELINES]: {
    path: RoutePath[AppRoutes.PIPELINES],
    element: <Pipelines />,
  },
  [AppRoutes.RUNS]: { path: RoutePath[AppRoutes.RUNS], element: <Runs /> },
  // TODO: creat not-found page
  [AppRoutes.NOT_FOUND]: {
    path: RoutePath[AppRoutes.NOT_FOUND],
    element: <Home />,
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
