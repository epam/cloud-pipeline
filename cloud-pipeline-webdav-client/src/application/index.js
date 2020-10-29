import React, {useCallback, useState, useLayoutEffect} from 'react';
import {Button, Layout} from 'antd';
import {SettingOutlined} from '@ant-design/icons';
import electron from 'electron';
import SplitPanel, {useSplitPanel} from './components/utilities/split-panel';
import FileSystemTab from './components/file-system-tab';
import Configuration from './components/configuration';
import Operations, {OPERATION_HEIGHT} from './operations';
import useFileSystem from './components/file-system-tab/use-file-system';
import useFileSystemTabActions from './use-file-system-tab-actions';
import Tabs, {RootDirectories} from './file-system-tabs';
import useTokenExpirationWarning from './components/utilities/use-token-expiration-warning';
import useHotKeys from './components/utilities/use-hot-keys';
import CreateDirectoryDialog from './components/file-system-tab/create-directory-dialog';
import './application.css';

function Application() {
  const leftTab = useFileSystem(Tabs.left, RootDirectories.left);
  const rightTab = useFileSystem(Tabs.right, RootDirectories.right);
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
    changeTab,
    clearSelection,
    moveCursor,
    moveToSelection,
    copy,
    move,
    remove,
    refresh,
    onCopyLeft,
    onCopyRight,
    onMoveLeft,
    onMoveRight,
    onRemoveLeft,
    onRemoveRight,
    onDropCommand,
    reInitialize,
    createDirectory,
    hotKeysBlocked,
  } = useFileSystemTabActions(leftTab, rightTab);
  const {
    createDirectoryHandler,
    onCreateDirectoryRequest,
    onCreateDirectoryLeft,
    onCreateDirectoryRight,
    onCancelCreateDirectory,
  } = createDirectory;
  const cfg = electron.remote.getGlobal('webdavClient');
  const settings = electron.remote.getGlobal('settings');
  const {config: webdavClientConfig = {}} = cfg || {};
  const {name: appName = 'Cloud Data'} = settings || {};
  useLayoutEffect(() => {
    document.title = appName;
  }, []);
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
  useTokenExpirationWarning(
    leftTab.initializeRequest,
    rightTab.initializeRequest
  );
  useHotKeys({
      changeTab,
      clearSelection,
      moveCursor,
      moveToSelection,
      refresh,
      copy,
      move,
      remove,
      createDirectory: onCreateDirectoryRequest
    },
    hotKeysBlocked
  );
  const [sizes, setPanelSizes] = useSplitPanel([undefined, undefined]);
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
        <SplitPanel
          style={{height: '100%'}}
          resizer={8}
          resizerStyle={{
            borderColor: '#ddd',
          }}
          sizes={sizes}
          onChange={setPanelSizes}
        >
          <div className="pane-container">
            <FileSystemTab
              identifier="left-tab"
              active={leftTabActive}
              error={leftTab.error}
              becomeActive={setLeftTabActive}
              contents={leftTab.contents}
              pending={leftTab.pending}
              path={leftTab.path}
              setPath={setLeftPath}
              selection={leftTab.selection}
              copyAllowed={leftTab.activeSelection.length > 0}
              moveAllowed={leftTab.activeSelection.length > 0}
              removeAllowed={leftTab.activeSelection.length > 0}
              lastSelectionIndex={leftTab.lastSelectionIndex}
              selectItem={leftTab.selectItem}
              onRefresh={leftTab.onRefresh}
              className="file-system-tab"
              fileSystem={leftTab.fileSystem}
              oppositeFileSystemReady={leftTabReady}
              onCopy={onCopyLeft}
              onMove={onMoveLeft}
              onRemove={onRemoveLeft}
              onCreateDirectory={onCreateDirectoryLeft}
              dragging={leftTab.fileSystem && dragging === leftTab.fileSystem.identifier}
              setDragging={setDragging}
              onDropCommand={onDropCommand}
              sorting={leftTab.sorting}
              setSorting={leftTab.setSorting}
            />
          </div>
          <div className="pane-container">
            <FileSystemTab
              identifier="right-tab"
              active={rightTabActive}
              error={rightTab.error}
              becomeActive={setRightTabActive}
              contents={rightTab.contents}
              pending={rightTab.pending}
              path={rightTab.path}
              setPath={setRightPath}
              selection={rightTab.selection}
              copyAllowed={rightTab.activeSelection.length > 0}
              moveAllowed={rightTab.activeSelection.length > 0}
              removeAllowed={rightTab.activeSelection.length > 0}
              lastSelectionIndex={rightTab.lastSelectionIndex}
              selectItem={rightTab.selectItem}
              onRefresh={rightTab.onRefresh}
              className="file-system-tab"
              fileSystem={rightTab.fileSystem}
              oppositeFileSystemReady={rightTabReady}
              onCopy={onCopyRight}
              onMove={onMoveRight}
              onRemove={onRemoveRight}
              onCreateDirectory={onCreateDirectoryRight}
              dragging={rightTab.fileSystem && dragging === rightTab.fileSystem.identifier}
              setDragging={setDragging}
              onDropCommand={onDropCommand}
              sorting={rightTab.sorting}
              setSorting={rightTab.setSorting}
            />
          </div>
        </SplitPanel>
        <div id="drag-and-drop" className="drag-and-drop">{'\u00A0'}</div>
        <CreateDirectoryDialog
          visible={!!createDirectoryHandler}
          onClose={onCancelCreateDirectory}
          onCreate={createDirectoryHandler}
        />
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
