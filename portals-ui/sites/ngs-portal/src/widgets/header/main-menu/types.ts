import { AppRoutes } from '../../../shared/constants/routes.ts';
import { ReactNode } from 'react';

export type MainMenuItem = {
  key: string;
  uri: string;
  routes: AppRoutes[];
  caption: ReactNode;
};
