import { AppRoutes, RoutePath } from '../../../shared/constants/routes.ts';
import { MainMenuItem } from './types.ts';

export const mainMenuItems: MainMenuItem[] = [
  {
    key: 'home',
    routes: [AppRoutes.HOME],
    uri: RoutePath[AppRoutes.HOME],
    caption: 'Home',
  },
  {
    key: 'projects',
    routes: [AppRoutes.PROJECTS, AppRoutes.PROJECT],
    uri: RoutePath[AppRoutes.PROJECTS],
    caption: 'Projects',
  },
  {
    key: 'pipelines',
    routes: [AppRoutes.PIPELINES, AppRoutes.PIPELINE],
    uri: RoutePath[AppRoutes.PIPELINES],
    caption: 'Pipelines',
  },
  {
    key: 'runs',
    routes: [AppRoutes.RUNS],
    uri: RoutePath[AppRoutes.RUNS],
    caption: 'Runs',
  },
];
