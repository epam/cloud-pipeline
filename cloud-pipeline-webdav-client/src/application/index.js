import React, {useCallback, useState} from 'react';
import {ipcRenderer} from 'electron';
import {Layout} from 'antd';
import {FileSystems} from './models/file-systems';
import FileSystemTab from './components/file-system-tab';
import './application.css';

const LEFT_TAB_ID = 0;
const RIGHT_TAB_ID = 1;

const Tabs = {
  left: FileSystems.local,
  right: FileSystems.webdav,
}

function Application() {
  const [leftTabReady, setLeftTabReady] = useState(false);
  const [rightTabReady, setRightTabReady] = useState(false);
  const [leftPath, setLeftPath] = useState(undefined);
  const [rightPath, setRightPath] = useState(undefined);
  const [activeTab, setActiveTab] = useState(LEFT_TAB_ID);
  const setLeftTabActive = useCallback(() => {
    setActiveTab(LEFT_TAB_ID);
  }, [setActiveTab]);
  const setRightTabActive = useCallback(() => {
    setActiveTab(RIGHT_TAB_ID);
  }, [setActiveTab]);
  const onCommand = useCallback((
    sourceFS,
    destinationFS,
    destinationPath,
    command,
    sourcePath,
    sources,
  ) => {
    ipcRenderer.send(
      'operation-start',
      command,
      {
        fs: sourceFS,
        path: sourcePath,
        elements: sources,
      },
      {
        fs: destinationFS,
        path: destinationPath,
      },
    );
  }, []);
  const onLeftFSCommand = useCallback((...args) => {
    onCommand(
      Tabs.left,
      Tabs.right,
      rightPath,
      ...args
    );
  }, [onCommand, rightPath]);
  const onRightFSCommand = useCallback((...args) => {
    onCommand(
      Tabs.right,
      Tabs.left,
      leftPath,
      ...args
    );
  }, [onCommand, leftPath]);
  return (
    <Layout className="layout">
      <Layout.Content className="content">
        <FileSystemTab
          fileSystem={Tabs.left}
          className="file-system-tab"
          oppositeFileSystemReady={rightTabReady}
          onFileSystemStatusChanged={setLeftTabReady}
          active={activeTab === LEFT_TAB_ID}
          becomeActive={setLeftTabActive}
          onPathChanged={setLeftPath}
          onCommand={onLeftFSCommand}
        />
        <FileSystemTab
          fileSystem={Tabs.right}
          className="file-system-tab"
          oppositeFileSystemReady={leftTabReady}
          onFileSystemStatusChanged={setRightTabReady}
          active={activeTab === RIGHT_TAB_ID}
          becomeActive={setRightTabActive}
          onPathChanged={setRightPath}
          onCommand={onRightFSCommand}
        />
      </Layout.Content>
      <Layout.Footer className="footer">
        Background operations will be listed here
      </Layout.Footer>
    </Layout>
  );
}

export default Application;
