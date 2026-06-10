import {ComponentType, useEffect, useMemo, useRef} from 'react';
import classNames from 'classnames';
import {CloseOutlined, QuestionCircleFilled} from '@ant-design/icons';
import {Tooltip} from 'antd';
import {useStore} from 'zustand';
import PanelIcons from '../home/layout/panel-icons';
import PanelInfos from '../home/layout/panel-informations';
import PanelTitles from '../home/layout/panel-titles';
import Panels from '../home/layout/panels';
import {
  ActivitiesPanel,
  MyActiveRunsPanel,
  MyDataPanel,
  MyPipelinesPanel,
  MyProjectsPanel,
  MyServicesPanel,
  NotificationsPanel,
  PersonalToolsPanel,
  RecentlyCompletedRunsPanel,
  UserCostsPanel,
} from '../home/panels';
import localization from '../../../utils/localization';
import styles from '../home/HomePage.module.css';
import {useHomeDataSources, useHomePanelRegistration, useLegacyRouter} from './store/hooks';
import {homeStore} from './store/home-store';
import type {PanelHandle} from './store/types';

const PanelComponent: Record<string, ComponentType<any>> = {
  [Panels.activities]: ActivitiesPanel,
  [Panels.data]: MyDataPanel,
  [Panels.services]: MyServicesPanel,
  [Panels.runs]: MyActiveRunsPanel,
  [Panels.notifications]: NotificationsPanel,
  [Panels.personalTools]: PersonalToolsPanel,
  [Panels.pipelines]: MyPipelinesPanel,
  [Panels.projects]: MyProjectsPanel,
  [Panels.recentlyCompletedRuns]: RecentlyCompletedRunsPanel,
  [Panels.userCosts]: UserCostsPanel,
};

type HomePagePanelProps = {
  panelKey: string;
  closable?: boolean;
};

function HomePagePanel({panelKey, closable = true}: HomePagePanelProps) {
  const router = useLegacyRouter();
  const refreshAll = useStore(homeStore, (state) => state.refreshAll);
  const dataSources = useHomeDataSources();
  const registerPanel = useHomePanelRegistration(panelKey);
  const removePanel = useStore(homeStore, (state) => state.removePanel);
  const contentPanelRef = useRef<PanelHandle['contentPanel']>(undefined);

  const panelHandle = useMemo<PanelHandle>(
    () => ({
      get contentPanel() {
        return contentPanelRef.current;
      },
      update() {
        contentPanelRef.current?.update?.();
      },
    }),
    [],
  );

  useEffect(() => {
    registerPanel(panelHandle);
    return () => registerPanel(null);
  }, [panelHandle, registerPanel]);

  const localizedString = localization.localization.localizedString;
  let title = PanelTitles[panelKey as keyof typeof PanelTitles];
  if (typeof title === 'function') {
    title = title(localizedString);
  }
  let info = PanelInfos[panelKey as keyof typeof PanelInfos];
  if (typeof info === 'function') {
    info = info(localizedString);
  }
  const PanelIcon = PanelIcons[panelKey as keyof typeof PanelIcons];
  const Panel = PanelComponent[panelKey];

  const onCloseClicked = (event?: React.MouseEvent) => {
    event?.preventDefault();
    removePanel(panelKey);
  };

  const initializeContent = (content: PanelHandle['contentPanel']) => {
    contentPanelRef.current = content;
  };

  if (!dataSources) {
    return null;
  }

  return (
    <div className={classNames(styles.panel, 'cp-panel')}>
      <div
        className={styles.panelHeader}
        style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}
      >
        <span className={styles.panelHeaderTitle}>
          {PanelIcon ? (
            <PanelIcon
              style={{
                fontSize: 'larger',
                marginRight: 5,
              }}
            />
          ) : null}
          {title}
        </span>
        <div className={styles.panelHeaderActions}>
          {info ? (
            <Tooltip title={info} placement="left">
              <QuestionCircleFilled style={{fontSize: 'larger'}} />
            </Tooltip>
          ) : null}
          {closable ? (
            <CloseOutlined
              onClick={onCloseClicked}
              style={{fontSize: 'larger'}}
              className={styles.panelHeaderCloseIcon}
            />
          ) : null}
        </div>
      </div>
      <div className={styles.panelContent}>
        {Panel ? (
          <Panel
            panelKey={panelKey}
            onInitialize={initializeContent}
            router={router}
            refresh={refreshAll}
            completedRuns={dataSources.completedRuns}
            activeRuns={dataSources.activeRuns}
            services={dataSources.services}
          />
        ) : null}
      </div>
    </div>
  );
}

export {HomePagePanel};
