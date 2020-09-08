import React, {useCallback, useState} from 'react';
import {Button, Layout} from 'antd';
import {SettingOutlined} from '@ant-design/icons';
import electron from 'electron';
import FileSystemTab from './components/file-system-tab';
import Configuration from './components/configuration';
import Operations, {OPERATION_HEIGHT} from './operations';
import useFileSystem from './components/file-system-tab/use-file-system';
import useFileSystemTabActions from './use-file-system-tab-actions';
import Tabs from './file-system-tabs';
import './application.css';

function Application() {
  const leftTab = useFileSystem(Tabs.left);
  const rightTab = useFileSystem(Tabs.right);
  const {
    operations,
    leftTabActive,
    rightTabActive,
    leftTabReady,
    rightTabReady,
    setLeftPath,
    setRightPath,
    setLeftTabActive,
    setRightTabActive,
    onLeftFSCommand,
    onRightFSCommand,
    onDropCommand,
    reInitialize,
  } = useFileSystemTabActions(leftTab, rightTab);
  const cfg = electron.remote.getGlobal('webdavClient');
  const {config: webdavClientConfig = {}} = cfg || {};
  const [
    configurationTabVisible,
    setConfigurationTabVisible,
  ] = useState(
    !webdavClientConfig.server ||
    !webdavClientConfig.username ||
    !webdavClientConfig.password
  );
  const onOpenConfigurationTab = useCallback(() => {
    setConfigurationTabVisible(true);
  }, [setConfigurationTabVisible]);
  const onCloseConfigurationTab = useCallback((modified) => {
    setConfigurationTabVisible(false);
    if (modified) {
      reInitialize();
    }
  }, [setConfigurationTabVisible, reInitialize]);
  const [dragging, setDragging] = useState(undefined);
  const activeOperations = operations.filter(o => !o.finished);
  return (
    <Layout className="layout">
      <Layout.Header
        className="header"
      >
        <Button
          size="small"
          onClick={onOpenConfigurationTab}
        >
          <SettingOutlined />
        </Button>
      </Layout.Header>
      <Layout.Content className="content">
        <Configuration
          visible={configurationTabVisible}
          onClose={onCloseConfigurationTab}
        />
        <FileSystemTab
          active={leftTabActive}
          error={leftTab.error}
          becomeActive={setLeftTabActive}
          contents={leftTab.contents}
          pending={leftTab.pending}
          path={leftTab.path}
          setPath={setLeftPath}
          selection={leftTab.selection}
          setSelection={leftTab.setSelection}
          lastSelectionIndex={leftTab.lastSelectionIndex}
          setLastSelectionIndex={leftTab.setLastSelectionIndex}
          onRefresh={leftTab.onRefresh}
          className="file-system-tab"
          fileSystem={leftTab.fileSystem}
          oppositeFileSystemReady={leftTabReady}
          onCommand={onLeftFSCommand}
          dragging={leftTab.fileSystem && dragging === leftTab.fileSystem.identifier}
          setDragging={setDragging}
          onDropCommand={onDropCommand}
        />
        <FileSystemTab
          active={rightTabActive}
          error={rightTab.error}
          becomeActive={setRightTabActive}
          contents={rightTab.contents}
          pending={rightTab.pending}
          path={rightTab.path}
          setPath={setRightPath}
          selection={rightTab.selection}
          setSelection={rightTab.setSelection}
          lastSelectionIndex={rightTab.lastSelectionIndex}
          setLastSelectionIndex={rightTab.setLastSelectionIndex}
          onRefresh={rightTab.onRefresh}
          className="file-system-tab"
          fileSystem={rightTab.fileSystem}
          oppositeFileSystemReady={rightTabReady}
          onCommand={onRightFSCommand}
          dragging={rightTab.fileSystem && dragging === rightTab.fileSystem.identifier}
          setDragging={setDragging}
          onDropCommand={onDropCommand}
        />
        <div id="drag-and-drop" className="drag-and-drop">{'\u00A0'}</div>
      </Layout.Content>
      <Layout.Footer
        className="footer"
        style={{
          height: activeOperations.length > 0
            ? activeOperations.length * OPERATION_HEIGHT + 4
            : undefined
        }}
      >
        <Operations
          className="operations"
          operations={activeOperations}
        />
      </Layout.Footer>
    </Layout>
  );
}

export default Application;
