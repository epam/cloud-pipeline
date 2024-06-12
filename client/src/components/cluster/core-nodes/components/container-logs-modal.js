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
  Modal,
  Spin,
  message
} from 'antd';
import ContainerLogs from '../../../../models/cluster/ContainerLogs';
import RunTaskLogs from '../../../runs/run-task-logs';
import styles from './container-logs-modal.css';

@observer
export default class ContainerLogsModal extends React.Component {
  @observable _logs;
  @observable _pending = false;

  componentDidMount () {
    this.fetchLogs();
  }

  componentDidUpdate (prevProps) {
    if (this.props.container !== prevProps.container) {
      this._logs = undefined;
      this.fetchLogs();
    }
  }

  @computed
  get logs () {
    return this._logs;
  }

  @computed
  get pending () {
    return this._pending;
  }

  fetchLogs = async () => {
    const {container} = this.props;
    if (!container) {
      return;
    }
    this._pending = true;
    const request = new ContainerLogs(container.podName, container.name);
    await request.fetch();
    if (request.error) {
      this._pending = false;
      return message.error(request.error, 5);
    }
    this._logs = request.value;
    this._pending = false;
  };

  onCancel = () => {
    const {onClose} = this.props;
    onClose && onClose();
  };

  render () {
    const {container} = this.props;
    return (
      <Modal
        visible={!!container}
        onCancel={this.onCancel}
        width={'60vw'}
        bodyStyle={{paddingTop: '5px'}}
        title={`Container ${container?.name || ''} logs`}
        footer={
          <Button
            type="primary"
            onClick={this.onCancel}>
            OK
          </Button>
        }
      >
        <Spin spinning={this.pending}>
          <div style={{
            display: 'flex',
            paddingBottom: 5,
            justifyContent: 'flex-end'
          }}>
            <a onClick={this.fetchLogs}>
              Refresh logs
            </a>
          </div>
          <RunTaskLogs
            className={styles.logsConsole}
            lineClassName={styles.consoleLine}
            logs={this.logs}
            showDate={false}
            showLineNumber
            searchAvailable
            downloadCurrentLog
            fileName={`${(container || {}).name}-logs`}
          />
        </Spin>
      </Modal>
    );
  }
};
