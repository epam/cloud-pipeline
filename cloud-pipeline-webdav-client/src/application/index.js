import React from 'react';
import {Layout} from 'antd';
import {FileSystems} from './models/file-systems';
import FileSystemTab from './components/file-system-tab';
import './application.css';

function Application() {
  return (
    <Layout className="layout">
      <Layout.Header className="header">
        Header
      </Layout.Header>
      <Layout.Content className="content">
        <FileSystemTab fileSystem={FileSystems.local} className="file-system-tab" />
        <FileSystemTab fileSystem={FileSystems.webdav} className="file-system-tab" />
      </Layout.Content>
      <Layout.Footer className="footer">
        Footer
      </Layout.Footer>
    </Layout>
  );
}

export default Application;
