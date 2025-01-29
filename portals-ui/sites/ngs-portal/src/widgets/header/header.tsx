import { Logo } from './logo';
import { AppRoutes, RoutePath } from '../../shared/constants/routes';
import { useNavigate } from 'react-router';
import { useCallback } from 'react';
import { NgsUserCard } from '../cards';
import { MainMenu } from './main-menu/main-menu.tsx';
import { useAuthenticatedUser } from '../../state/authentication/hooks.ts';

export const Header = () => {
  const authenticatedUser = useAuthenticatedUser();
  const navigate = useNavigate();

  const onLogoClick = useCallback(() => {
    navigate(RoutePath[AppRoutes.HOME]);
  }, [navigate]);

  return (
    <header className="min-h-[46px] bg-slate-800 flex justify-between items-center gap-4 px-4">
      <Logo onClick={onLogoClick} />
      <MainMenu />
      <div className="text-white text-sm">
        {authenticatedUser && (
          <NgsUserCard userName={authenticatedUser?.userName} showIcon />
        )}
      </div>
    </header>
  );
};
