import {Outlet} from 'react-router-dom';
import {PageSuspense} from './page-suspense';

/**
 * Full-viewport layout without the navigation panel.
 * Used for embedded viewers opened in a separate window/tab.
 */
function FullscreenLayout() {
  return (
    <div className="h-full w-full overflow-auto">
      <PageSuspense>
        <Outlet />
      </PageSuspense>
    </div>
  );
}

export {FullscreenLayout};
