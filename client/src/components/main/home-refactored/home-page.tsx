import {useCallback} from 'react';
import classNames from 'classnames';
import GridLayout from 'react-grid-layout/legacy';
import {SettingOutlined} from '@ant-design/icons';
import {Alert, Button} from 'antd';
import {useStore} from 'zustand';
import LoadingView from '../../special/LoadingView.tsx';
import {GridStyles} from '../home/layout';
import {useStringPreferenceValue} from '../../../queries/preferences/hooks.ts';
import {preferenceNames} from '../../../stores/preferences/names.ts';
import styles from '../home/HomePage.module.css';
import 'react-resizable/css/styles.css';
import 'react-grid-layout/css/styles.css';
import '../../../staticStyles/HomePage.css';
import {ConfigureHomePage} from './configure-home-page';
import {HomeLegacyStoresProvider} from './legacy-stores-provider';
import {HomePagePanel} from './home-page-panel';
import {homeStore} from './store/home-store';
import {
  useHomeConfigureModalVisible,
  useHomeContainerDimensions,
  useHomeInitialization,
  useHomePanelsLayout,
  useHomePolling,
} from './store/hooks';

function HomePageContent() {
  const deploymentName = useStringPreferenceValue(preferenceNames.uiPipelineDeploymentName);
  const {layoutLoaded, layoutError} = useHomeInitialization();
  const panelsLayout = useHomePanelsLayout();
  const configureModalVisible = useHomeConfigureModalVisible();
  const setConfigureModalVisible = useStore(homeStore, (state) => state.setConfigureModalVisible);
  const onLayoutChanged = useStore(homeStore, (state) => state.onLayoutChanged);
  const syncPanelsLayout = useStore(homeStore, (state) => state.syncPanelsLayout);
  const {assignContainerRef, containerWidth, containerHeight} = useHomeContainerDimensions();
  useHomePolling();

  const openConfigureModal = useCallback(() => {
    setConfigureModalVisible(true);
  }, [setConfigureModalVisible]);

  const closeConfigureModal = useCallback(() => {
    setConfigureModalVisible(false);
    syncPanelsLayout();
  }, [setConfigureModalVisible, syncPanelsLayout]);

  if (!layoutLoaded) {
    return <LoadingView className={undefined} style={undefined} />;
  }

  if (layoutError) {
    return <Alert type="error" title={layoutError} />;
  }

  return (
    <div ref={assignContainerRef} className={styles.globalContainer}>
      <div
        className={styles.stickyHeader}
        style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}
      >
        <h1>{deploymentName || ''} Dashboard</h1>
        <div className={classNames(styles.stickyHeaderBackground, 'cp-dashboard-sticky-panel')}>
          {'\u00A0'}
        </div>
        <Button onClick={openConfigureModal}>
          <SettingOutlined />
          Configure
        </Button>
      </div>
      <div style={{paddingTop: GridStyles.top}} className={styles.container}>
        {containerWidth !== null && containerHeight !== null ? (
          <GridLayout
            className="layout"
            draggableHandle={`.${styles.panelHeader}`}
            layout={panelsLayout}
            cols={GridStyles.gridCols}
            width={containerWidth - GridStyles.scrollBarSize}
            margin={[GridStyles.panelMargin, GridStyles.panelMargin]}
            containerPadding={[0, 0]}
            rowHeight={GridStyles.rowHeight(containerHeight)}
            onDragStop={(layout) => onLayoutChanged([...layout], true)}
            onLayoutChange={(layout) => onLayoutChanged([...layout], false)}
          >
            {panelsLayout.map((item) => (
              <div key={item.i}>
                <HomePagePanel panelKey={item.i} closable={panelsLayout.length > 1} />
              </div>
            ))}
          </GridLayout>
        ) : null}
      </div>
      <ConfigureHomePage
        visible={configureModalVisible}
        onCancel={closeConfigureModal}
        onSave={closeConfigureModal}
      />
    </div>
  );
}

function HomePage() {
  return (
    <HomeLegacyStoresProvider>
      <HomePageContent />
    </HomeLegacyStoresProvider>
  );
}

export {HomePage};
