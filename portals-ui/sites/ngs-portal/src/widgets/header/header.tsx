import { Logo } from './logo';
import { authenticationStore } from '../../state/authentication/store';
import { AppRoutes, RoutePath } from '../../shared/constants/routes';
import type { RouteLink } from './types.ts';
import { HeaderRouteLink } from './header-route-link.tsx';
import { useNavigate } from 'react-router';
import { useCallback } from 'react';

const LINKS: RouteLink[] = [
  { route: AppRoutes.HOME, caption: 'Home' },
  { route: AppRoutes.PROJECTS, caption: 'Projects' },
  { route: AppRoutes.PIPELINES, caption: 'Pipelines' },
  { route: AppRoutes.RUNS, caption: 'Runs' },
];

export const Header = () => {
  const { authenticatedUser } = authenticationStore.getState();
  const navigate = useNavigate();
  const onLogoClick = useCallback(() => {
    navigate(RoutePath[AppRoutes.HOME]);
  }, [navigate]);
  return (
    <header className="bg-[var(--uui-secondary-70)] flex justify-between items-center gap-4 px-4">
      <Logo onClick={onLogoClick} />

      <div className="flex">
        {LINKS.map((route) => (
          <HeaderRouteLink key={route.route} link={route} />
        ))}
      </div>

      <div className="ml-auto">
        <p className="text-white text-sm">
          {authenticatedUser?.userName ?? 'Login'}
        </p>
      </div>
    </header>
  );
};
