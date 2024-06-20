/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import {observable, computed} from 'mobx';
import {observer} from 'mobx-react';
import {
  Button,
  Spin,
  Modal,
  Alert,
  Tabs,
  message
} from 'antd';
import CorePodsInfo from '../../../../models/cluster/CorePodsInfo';
import CodeEditor from '../../../special/CodeEditor';
import {formatPodDescriptionString} from '../utils';

const TAB_KEYS = {
  events: 'events',
  info: 'info'
};

@observer
export default class PodInfoModal extends React.Component {
  state = {
    activeTabKey: TAB_KEYS.info
  };

  @observable _info;
  @observable _pending = false;

  componentDidMount () {
    this.fetchInfo();
  }

  componentDidUpdate (prevProps) {
    if (this.props.pod !== prevProps.pod) {
      this._info = undefined;
      this.resetActiveTab();
      this.fetchInfo();
    }
  }

  @computed
  get events () {
    if (!this._info) {
      return [];
    }
    return this._info.events || [];
  }

  @computed
  get description () {
    if (!this._info) {
      return '{}';
    }
    return this._info.description || '{}';
  }

  @computed
  get pending () {
    return this._pending;
  }

  fetchInfo = async () => {
    const {pod} = this.props;
    if (!pod || this._detailedInfo) {
      return;
    }
    this._pending = true;
    const request = new CorePodsInfo(pod.name, true);
    await request.fetch();
    if (request.error) {
      this._pending = false;
      return message.error(request.error, 5);
    }
    this._info = request.value;
    this._pending = false;
  };

  onCancel = () => {
    const {onClose} = this.props;
    onClose && onClose();
  };

  renderEventsTab = () => {
    if (this.pending) {
      return null;
    }
    if (!this.events.length) {
      return (
        <div>
          No events information
        </div>
      );
    }
    const getAlertType = (podEvent) => {
      const podEventType = (podEvent.type || '').toLowerCase();
      if (podEventType === 'warning') {
        return 'error';
      }
      if (podEventType === 'normal') {
        return 'info';
      }
      return 'warning';
    };
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 5,
        maxHeight: '60vh',
        overflow: 'auto'
      }}>
        {this.events.map((podEvent) => (
          <Alert
            key={`${podEvent.message}-${podEvent.type}`}
            message={(
              <div style={{
                display: 'flex',
                justifyContent: 'space-between'
              }}>
                <span>{podEvent.message}</span>
                <span style={{paddingLeft: 10, whiteSpace: 'nowrap'}}>
                  Reason: {podEvent.reason}
                </span>
              </div>
            )}
            type={getAlertType(podEvent)}
          />
        ))}
      </div>
    );
  };

  renderInformationTab = () => {
    if (this.pending) {
      return null;
    }
    if (!this.description) {
      return (
        <div>
          No info
        </div>
      );
    }
    const [description, error] = formatPodDescriptionString(this.description);
    if (error) {
      message.error(error, 5);
    }
    return (
      <div style={{
        maxHeight: '60vh',
        overflow: 'auto'
      }}>
        <CodeEditor
          readOnly
          delayedUpdate
          language="javascript"
          lineWrapping
          code={description}
        />
      </div>
    );
  };

  onChangeTab = (key) => {
    this.setState({activeTabKey: key}, this.fetchInfo);
  };

  resetActiveTab = () => this.setState({activeTabKey: TAB_KEYS.info});

  render () {
    const {pod} = this.props;
    const {activeTabKey} = this.state;
    const tabs = [
      {
        key: TAB_KEYS.info,
        title: 'Information',
        render: this.renderInformationTab,
        visible: () => true
      },
      {
        key: TAB_KEYS.events,
        title: 'Events',
        render: this.renderEventsTab,
        visible: () => this.events.length
      }
    ].filter(tab => tab.visible());
    return (
      <Modal
        visible={!!pod}
        onCancel={this.onCancel}
        width={'60vw'}
        cancelText={null}
        footer={
          <Button
            type="primary"
            onClick={this.onCancel}>
            OK
          </Button>
        }
      >
        <div style={{display: 'flex', flex: 1}}>
          <Tabs
            style={{flex: 1}}
            activeKey={activeTabKey}
            onChange={this.onChangeTab}
            size="small"
          >
            {tabs.map(tab => (
              <Tabs.TabPane tab={tab.title} key={tab.key}>
                <Spin spinning={this.pending}>
                  {tab.render()}
                </Spin>
              </Tabs.TabPane>
            ))}
          </Tabs>
        </div>
      </Modal>
    );
  }
};
