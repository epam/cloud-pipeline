import React from 'react';
import { Button, Layout } from 'antd';
import { InboxOutlined, SettingOutlined } from '@ant-design/icons';
import FileSystems from './file-systems';
import Operations from './operations';
import ipcAction from '../common/ipc-action';
import AutoUpdate from './auto-update';
import './application.css';
import useConfiguration from "../common/use-configuration";

function openConfigurationWindow() {
  ipcAction('openConfigurationWindow');
}

function openStorageAccessWindow() {
  ipcAction('openStorageAccessWindow');
}

function Application() {
  const {configuration} = useConfiguration();
  console.log(configuration);
  return (
    <div className="container">
      <Layout className="layout">
        {
          (configuration?.displayBucketSelection !== false || configuration?. displaySettings !== false) && (
            <Layout.Header className="header">
              {
                configuration?.displayBucketSelection !== false && (
                  <Button
                    size="small"
                    onClick={openStorageAccessWindow}
                  >
                    <InboxOutlined />
                  </Button>
                )
              }
              {
                configuration?.displaySettings !== false && (
                  <Button
                    size="small"
                    onClick={openConfigurationWindow}
                  >
                    <SettingOutlined />
                  </Button>
                )
              }
            </Layout.Header>
          )
        }
        <Layout.Content className="content">
          <FileSystems
            className="content-split-panel"
            tabClassName="file-system-tab"
          />
        </Layout.Content>
        <Layout.Footer
          className="footer"
          style={{
            height: 'unset',
          }}
        >
          <Operations
            className="operations"
          />
        </Layout.Footer>
      </Layout>
      <AutoUpdate />
    </div>
  );
}

export default Application;
