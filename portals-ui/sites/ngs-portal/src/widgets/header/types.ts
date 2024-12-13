import type { AppRoutes } from '../../shared/constants/routes.ts';
import type { ReactNode } from 'react';

export type RouteLink = {
  route: AppRoutes;
  caption: ReactNode;
};

export type HeaderRouteLinkProps = {
  link: RouteLink;
};
