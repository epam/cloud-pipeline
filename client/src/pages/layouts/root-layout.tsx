import {Outlet} from 'react-router-dom';
import {GlobalOverlays} from './global-overlays';

/**
 * Top-level route wrapper: renders child routes plus global shell overlays
 * (RunModal, NotificationCenter, RunContinuationConfirmation) on every route.
 */
function RootLayout() {
  return (
    <>
      <Outlet />
      <GlobalOverlays />
    </>
  );
}

export {RootLayout};
