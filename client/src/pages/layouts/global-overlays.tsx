import {useCallback, useMemo} from 'react';
import {useLocation, useNavigate, useParams} from 'react-router-dom';
import RunModal from '../../components/main/RunModal';
import NotificationCenter from '../../components/main/notification/NotificationCenter';
import {RunContinuationConfirmation} from '../../components/runs/actions/continue-run';
import {navigationPages} from '../../routing/paths.ts';
import {useActiveNavigationKey} from '../../stores/ui-navigation/hooks.ts';
import {LegacyMobXStoresProvider} from '../_shared/legacy-mobx-stores-provider.tsx';

function GlobalOverlays() {
  const navigate = useNavigate();
  const params = useParams();
  const location = useLocation();
  const replace = useCallback((to: string) => navigate(to, {replace: true}), [navigate]);
  const router = useMemo(
    () => ({
      push: navigate,
      replace,
      location,
      params,
    }),
    [navigate, replace, location, params],
  );
  const activeKey = useActiveNavigationKey(location.pathname);

  return (
    <LegacyMobXStoresProvider>
      <NotificationCenter
        delaySeconds={2}
        disableEmailNotifications={activeKey === navigationPages.notifications}
        router={router}
      />
      <RunModal />
      <RunContinuationConfirmation />
    </LegacyMobXStoresProvider>
  );
}

export {GlobalOverlays};
