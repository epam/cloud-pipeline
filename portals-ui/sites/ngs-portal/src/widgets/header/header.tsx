import { TabButton } from '@epam/uui';
import { useState } from 'react';
import { Logo } from './logo';
import { authenticationStore } from '../../state/authentication/store';
import { RoutePath } from '../../shared/constants/routes';

const LINKS = [
  { pathname: RoutePath.home, caption: 'Home' },
  { pathname: RoutePath.projects, caption: 'Projects' },
  { pathname: RoutePath.pipelines, caption: 'Pipelines' },
  { pathname: RoutePath.runs, caption: 'Runs' },
];

export const Header = () => {
  const { authenticatedUser } = authenticationStore.getState();
  const [activeLink, setActiveLink] = useState('/');

  return (
    <header className="bg-[var(--uui-secondary-70)] flex justify-between items-center gap-4 px-4">
      <Logo />

      <div className="flex">
        {LINKS.map(({ pathname, caption }) => (
          <TabButton
            cx="text-white"
            link={{ pathname }}
            caption={caption}
            isLinkActive={activeLink === pathname}
            onClick={() => setActiveLink(pathname)}
          />
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
