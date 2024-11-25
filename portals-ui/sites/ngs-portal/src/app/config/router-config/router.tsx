import type { RouteObject } from 'react-router-dom';
import { createHashRouter } from 'react-router-dom';
import { Layout } from '../../../pages/layout/index.tsx';
import { Home } from '../../../pages/home/home.tsx';
import Pipelines from '../../../pages/pipelines/index.tsx';
import Projects from '../../../pages/projects/index.tsx';
import Runs from '../../../pages/runs/index.tsx';
import { AppRoutes, RoutePath } from '../../../shared/constants/routes.ts';

const routerConfig: Record<AppRoutes, RouteObject> = {
  [AppRoutes.HOME]: { path: RoutePath.home, element: <Home /> },
  [AppRoutes.PROJECTS]: { path: RoutePath.projects, element: <Runs /> },
  [AppRoutes.PIPELINES]: { path: RoutePath.pipelines, element: <Pipelines /> },
  [AppRoutes.RUNS]: { path: RoutePath.runs, element: <Projects /> },
  // TODO: creat not-found page
  [AppRoutes.NOT_FOUND]: { path: RoutePath.not_found, element: <Home /> },
};

const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    children: Object.values(routerConfig),
  },
];

export const appRouter = createHashRouter(routes);
