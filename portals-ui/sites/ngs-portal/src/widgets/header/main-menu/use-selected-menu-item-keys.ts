import { matchRoutes, useLocation } from 'react-router';
import { useMemo } from 'react';
import { mainMenuItems } from './items';
import { routes } from '../../../app/config/router-config/router.tsx';

export function useSelectedMenuItemKeys(): string[] {
  const location = useLocation();
  const matchedRoutes = matchRoutes(routes, location.pathname);
  return useMemo(() => {
    if (!matchedRoutes) {
      return [];
    }
    for (const mainMenuItem of mainMenuItems) {
      if (
        matchedRoutes.some((r) =>
          mainMenuItem.routes.some((m) => m === r.route.ngsPortalRoute),
        )
      ) {
        return [mainMenuItem.key];
      }
    }
    return [];
  }, [matchedRoutes]);
}
