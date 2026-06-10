import classNames from 'classnames';
import {type ComponentType, useState} from 'react';
import {Link, useLocation, useNavigate} from 'react-router-dom';
import {LeftOutlined, RightOutlined} from '@ant-design/icons';
import {Button, Popover, Tooltip} from 'antd';
import CounterMenuItem from '../../components/main/navigation/CounterMenuItem';
import RunsFilterDescription from '../../components/runs/run-table/runs-filter-description';
import searchStyles from '../../components/search/search.module.css';
import SessionStorageWrapper from '../../components/special/SessionStorageWrapper';
import {navigationPages} from '../../routing/paths.ts';
import {
  useActiveNavigationKey,
  useLibraryExpanded,
  useNavigationItems,
  useSearchEnabled,
} from '../../stores/ui-navigation/hooks.ts';
import {
  useActiveRunsCount,
  useActiveRunsCounterFilter,
  useBillingNavigationEnabled,
  useEmailNotificationsNavigationEnabled,
  useNavigationItemContext,
  useNavigationPanelPolling,
  useUnreadNotificationsCount,
} from '../../stores/ui-navigation/navigation-panel-hooks.ts';
import {NavigationItem, NavigationItemContext} from '../../stores/ui-navigation/types.ts';
import invalidateEdgeTokens from '../../utils/invalidate-edge-tokens';
import {NavigationGuard} from '../routes/navigation-guard.tsx';
import {NavigationApplicationVersion} from './navigation-application-version.tsx';
import {NavigationSupportMenu} from './navigation-support-menu.tsx';

const RunsFilterTooltip = RunsFilterDescription as ComponentType<{
  filters?: {statuses?: string[]; onlyMasterJobs?: boolean};
}>;

type NavigationPanelProps = {
  searchControlVisible?: boolean;
};

function getNavigationItemTitle(
  title: NavigationItem['title'],
  context: NavigationItemContext,
): string | undefined {
  if (typeof title === 'function') {
    return title(context);
  }
  return title;
}

function isNavigationItemVisible(item: NavigationItem, context: NavigationItemContext): boolean {
  if (typeof item.visible === 'function') {
    return item.visible(context);
  }
  if (item.visible === undefined) {
    return true;
  }
  return !!item.visible;
}

function logout() {
  invalidateEdgeTokens().then(() => {
    let url = `${SERVER}/saml/logout`;
    if (SERVER.endsWith('/')) {
      url = `${SERVER}saml/logout`;
    }
    window.location.href = url;
  });
}

function NavigationPanel({searchControlVisible = false}: NavigationPanelProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const items = useNavigationItems();
  const activeKey = useActiveNavigationKey(location.pathname);
  const searchEnabled = useSearchEnabled();
  const itemContext = useNavigationItemContext();
  const billingEnabled = useBillingNavigationEnabled();
  const notificationsEnabled = useEmailNotificationsNavigationEnabled();
  const runsCount = useActiveRunsCount();
  const notificationsCount = useUnreadNotificationsCount();
  const runsCounterFilter = useActiveRunsCounterFilter();
  const [libraryExpanded, setLibraryExpanded] = useLibraryExpanded();
  const [versionInfoVisible, setVersionInfoVisible] = useState(false);
  const isLibraryActive = activeKey === navigationPages.library;

  useNavigationPanelPolling();

  const handleNavigate = (item: NavigationItem) => {
    if (item.key === navigationPages.runs) {
      SessionStorageWrapper.navigateToActiveRuns({push: navigate});
      return;
    }
    if (item.key === 'logout') {
      logout();
      return;
    }
    if (typeof item.action === 'function') {
      item.action(itemContext);
      return;
    }
    if (item.isLink && typeof item.path === 'string') {
      navigate(item.path);
    }
  };

  return (
    <>
      <NavigationGuard />
      <aside
        aria-label="Main navigation"
        className={classNames('cp-navigation-panel relative flex h-full shrink-0 flex-col', {
          impersonated: itemContext.impersonation?.isImpersonated,
        })}
        id="navigation-container"
      >
        <div
          className={classNames('relative flex h-full min-h-0 flex-col', searchStyles.searchBlur, {
            [searchStyles.enabled]: searchControlVisible,
          })}
        >
          <Popover
            content={<NavigationApplicationVersion />}
            onOpenChange={setVersionInfoVisible}
            open={versionInfoVisible}
            placement="right"
            trigger="click"
          >
            <Button className="cp-navigation-menu-item" id="navigation-button-logo" type="text">
              <div className="cp-navigation-item-logo">{'\u00A0'}</div>
            </Button>
          </Popover>
          <div className="flex min-h-0 flex-1 flex-col overflow-y-auto">
            {items
              .filter((item) => isNavigationItemVisible(item, itemContext))
              .map((item, index) => (
                <NavigationPanelItem
                  activeKey={activeKey}
                  billingEnabled={billingEnabled}
                  item={item}
                  itemContext={itemContext}
                  key={`${item.key}-${index}`}
                  notificationsCount={notificationsCount}
                  notificationsEnabled={notificationsEnabled}
                  onNavigate={handleNavigate}
                  runsCount={runsCount}
                  runsCounterFilter={runsCounterFilter}
                  searchEnabled={searchEnabled}
                />
              ))}
          </div>
          <NavigationSupportMenu isLibraryActive={isLibraryActive} />
          {isLibraryActive ? (
            <Button
              className="cp-navigation-menu-item"
              id="expand-collapse-library-tree-button"
              onClick={() => setLibraryExpanded(!libraryExpanded)}
              style={{position: 'absolute', left: 0, bottom: 0, right: 0}}
              type="text"
            >
              {libraryExpanded ? <LeftOutlined /> : <RightOutlined />}
            </Button>
          ) : null}
        </div>
      </aside>
    </>
  );
}

function NavigationPanelItem({
  item,
  activeKey,
  itemContext,
  searchEnabled,
  billingEnabled,
  notificationsEnabled,
  runsCount,
  notificationsCount,
  runsCounterFilter,
  onNavigate,
}: {
  item: NavigationItem;
  activeKey?: string;
  itemContext: NavigationItemContext;
  searchEnabled: boolean;
  billingEnabled: boolean;
  notificationsEnabled: boolean;
  runsCount: number;
  notificationsCount: number;
  runsCounterFilter: {statuses: string[]; onlyMasterJobs: boolean};
  onNavigate: (item: NavigationItem) => void;
}) {
  if (item.isDivider) {
    return <div className="cp-divider horizontal cp-navigation-divider" />;
  }

  if (item.key === navigationPages.billing && !billingEnabled) {
    return null;
  }

  const Icon = item.icon;
  const title = getNavigationItemTitle(item.title, itemContext);
  const isActive = item.key === activeKey;
  const menuItemClassName = classNames('cp-navigation-menu-item', {selected: isActive});

  if (item.key === navigationPages.search) {
    if (!searchEnabled || !item.path) {
      return null;
    }
    return (
      <Tooltip mouseEnterDelay={0.5} placement="right" title={title}>
        <Link
          aria-current={isActive ? 'page' : undefined}
          aria-label={title}
          className={menuItemClassName}
          id={`navigation-button-${item.key}`}
          to={item.path}
        >
          {Icon ? <Icon style={item.iconStyle} /> : null}
        </Link>
      </Tooltip>
    );
  }

  if (item.key === navigationPages.runs) {
    return (
      <CounterMenuItem
        className={classNames(menuItemClassName, 'cp-runs-menu-item', {
          active: runsCount > 0,
        })}
        count={runsCount}
        icon={Icon}
        id={`navigation-button-${item.key}`}
        maxCount={Infinity}
        onClick={() => onNavigate(item)}
        tooltip={<RunsFilterTooltip filters={runsCounterFilter} />}
      />
    );
  }

  if (item.key === navigationPages.notifications) {
    if (!notificationsEnabled) {
      return null;
    }
    return (
      <CounterMenuItem
        className={menuItemClassName}
        count={notificationsCount}
        icon={Icon}
        id={`navigation-button-${item.key}`}
        onClick={() => onNavigate(item)}
        tooltip={title}
      />
    );
  }

  if (item.isLink && item.path) {
    return (
      <Tooltip mouseEnterDelay={0.5} placement="right" title={title}>
        <Link
          aria-current={isActive ? 'page' : undefined}
          aria-label={title}
          className={menuItemClassName}
          id={`navigation-button-${item.key}`}
          to={item.path}
        >
          {Icon ? <Icon style={item.iconStyle} /> : null}
        </Link>
      </Tooltip>
    );
  }

  return (
    <Tooltip mouseEnterDelay={0.5} placement="right" title={title}>
      <Button
        aria-label={title}
        className={menuItemClassName}
        id={`navigation-button-${item.key}`}
        onClick={() => onNavigate(item)}
        type="text"
      >
        {Icon ? <Icon style={item.iconStyle} /> : null}
      </Button>
    </Tooltip>
  );
}

export {NavigationPanel};
