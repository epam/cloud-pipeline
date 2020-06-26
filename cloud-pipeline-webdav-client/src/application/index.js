import React, {useCallback, useState} from 'react';
import {Layout} from 'antd';
import {FileSystems} from './models/file-systems';
import FileSystemTab from './components/file-system-tab';
import './application.css';

const LEFT_TAB_ID = 0;
const RIGHT_TAB_ID = 1;

function Application() {
  const [localSystemTabReady, setLocalSystemTabReady] = useState(false);
  const [webdavSystemTabReady, setWebdavSystemTabReady] = useState(false);
  const [activeTab, setActiveTab] = useState(LEFT_TAB_ID);
  const setLocalSystemTabActive = useCallback(() => {
    setActiveTab(LEFT_TAB_ID);
  }, [setActiveTab]);
  const setWebdavSystemTabActive = useCallback(() => {
    setActiveTab(RIGHT_TAB_ID);
  }, [setActiveTab]);
  return (
    <Layout className="layout">
      <Layout.Content className="content">
        <FileSystemTab
          fileSystem={FileSystems.local}
          className="file-system-tab"
          oppositeFileSystemReady={webdavSystemTabReady}
          onFileSystemStatusChanged={setLocalSystemTabReady}
          active={activeTab === LEFT_TAB_ID}
          becomeActive={setLocalSystemTabActive}
        />
        <FileSystemTab
          fileSystem={FileSystems.webdav}
          className="file-system-tab"
          oppositeFileSystemReady={localSystemTabReady}
          onFileSystemStatusChanged={setWebdavSystemTabReady}
          active={activeTab === RIGHT_TAB_ID}
          becomeActive={setWebdavSystemTabActive}
        />
      </Layout.Content>
      <Layout.Footer className="footer">
        Background operations will be listed here
      </Layout.Footer>
    </Layout>
  );
}

export default Application;
