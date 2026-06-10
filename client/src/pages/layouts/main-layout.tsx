import classNames from 'classnames';
import {useState} from 'react';
import {Outlet} from 'react-router-dom';
import searchStyles from '../../components/search/search.module.css';
import {ErrorBoundary} from '../../components/shared/error-boundary/error-boundary';
import {MainOverlays} from './main-overlays';
import {NavigationPanel} from './navigation-panel';
import {PageSuspense} from './page-suspense';

/**
 * Standard app shell: navigation panel on the left, routed page content on the right.
 * All routes except fullscreen viewers (miew, wsi, hcs) are nested under this layout.
 */
function MainLayout() {
  const [searchFormVisible, setSearchFormVisible] = useState(false);

  return (
    <>
      <MainOverlays onVisibilityChanged={setSearchFormVisible} />
      <div className="flex h-full w-full overflow-hidden">
        <NavigationPanel searchControlVisible={searchFormVisible} />
        <main
          className={classNames(
            'min-w-0 flex-1 overflow-auto cp-panel-background-color',
            searchStyles.searchBlur,
            {
              [searchStyles.enabled]: searchFormVisible,
            },
          )}
        >
          <PageSuspense>
            <ErrorBoundary>
              <Outlet />
            </ErrorBoundary>
          </PageSuspense>
        </main>
      </div>
    </>
  );
}

export {MainLayout};
