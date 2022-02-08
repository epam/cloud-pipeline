/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import PipelineRunCmd from '../../../../../models/pipelines/PipelineRunCmd';
import {Alert, Modal, Row, Select, Tabs} from 'antd';
import {applyCustomCapabilitiesParameters} from './run-capabilities';
import styles from './launch-command.css';
import {API_PATH, SERVER} from '../../../../../config';
import {getOS, OperationSystems} from '../../../../../utils/OSDetection';
import BashCode from '../../../../special/bash-code';

const OSFamily = {
  windows: 'WINDOWS',
  linux: 'LINUX'
};

const DEFAULT_OS = getOS() === OperationSystems.windows
  ? OSFamily.windows
  : OSFamily.linux;

function wrapCommand (command, template) {
  if (!template) {
    return `# PIPE CLI command: \n${command || ''}`;
  }
  return template.replace(/\{LAUNCH_COMMAND\}/ig, command);
}

function wrapNewLines (command) {
  if (!command) {
    return '';
  }
  return command.replace(/\\\\n/g, '\n');
}

function generateRunMethodUrl () {
  const el = document.createElement('div');
  el.innerHTML = '<a href="' + (SERVER + API_PATH) + '/run"></a>';
  return el.firstChild.href;
}

class LaunchCommand extends React.Component {
  static propTypes = {
    onInitialized: PropTypes.func,
    payload: PropTypes.object,
    visible: PropTypes.bool,
    onClose: PropTypes.func
  };

  static requestIdentifier = 0;

  state = {
    pending: false,
    code: null,
    error: false,
    osType: DEFAULT_OS
  };

  @computed
  get launchCommandTemplate () {
    const {preferences} = this.props;
    return preferences.getPreferenceValue('ui.launch.command.template');
  }

  componentDidMount () {
    this.props.onInitialized && this.props.onInitialized(this);
    if (this.props.payload) {
      this.rebuild(this.props.payload);
    }
  }

  componentWillReceiveProps (nextProps, nextContext) {
    if (nextProps.visible !== this.props.visible && nextProps.visible) {
      this.rebuild(nextProps.payload);
    }
  }

  rebuild = (payload) => {
    const {preferences} = this.props;
    LaunchCommand.requestIdentifier += 1;
    const identifier = LaunchCommand.requestIdentifier;
    this.setState({pending: true}, async () => {
      await preferences.fetchIfNeededOrWait();
      const {osType} = this.state;
      const request = new PipelineRunCmd();
      const {
        params = {},
        ...payloadRest
      } = payload || {};
      const pipelineStart = {
        ...payloadRest,
        params: applyCustomCapabilitiesParameters(params, preferences)
      };
      await request.send({
        pipelineStart,
        quite: false,
        yes: true,
        showParams: false,
        sync: false,
        runStartCmdExecutionEnvironment: osType
      });
      if (identifier === LaunchCommand.requestIdentifier) {
        if (request.error) {
          this.setState({
            pending: false,
            code: null,
            error: request.error,
            payload: pipelineStart
          });
        } else {
          this.setState({
            pending: false,
            code: request.value,
            error: false,
            payload: pipelineStart
          });
        }
      }
    });
  };

  renderCLICommand () {
    const {
      code,
      error,
      osType
    } = this.state;
    if (error) {
      return (
        <Alert type="warning" message={error} />
      );
    }
    const onChangeOS = (os) => {
      this.setState({osType: os}, () => this.rebuild(this.props.payload));
    };
    return (
      <div>
        <div style={{display: 'flex', flexDirection: 'row', alignItems: 'center', marginBottom: 5}}>
          <span style={{marginRight: 5}}>Operation System:</span>
          <Select
            value={osType}
            onChange={onChangeOS}
            style={{width: 150}}
          >
            <Select.Option key={OSFamily.linux} value={OSFamily.linux}>
              Linux
            </Select.Option>
            <Select.Option key={OSFamily.windows} value={OSFamily.windows}>
              Windows
            </Select.Option>
          </Select>
        </div>
        <BashCode
          id="launch-command"
          className={styles.launchCommandCode}
          code={
            wrapNewLines(
              wrapCommand(code, this.launchCommandTemplate)
            )
          }
        />
      </div>
    );
  }

  renderAPICommand () {
    const {payload} = this.state;
    if (!payload) {
      return (
        <Alert type="warning" message="Payload is not set" />
      );
    }
    return (
      <div className={styles.launchCommandCode}>
        <Row className={styles.endpoint} type="flex">
          <span className={styles.method}>POST</span>
          <span className={styles.url}>{generateRunMethodUrl()}</span>
        </Row>
        <BashCode
          id="launch-command"
          code={JSON.stringify(payload, null, ' ')}
          style={{maxHeight: '50vh', overflow: 'auto'}}
        />
      </div>
    );
  }

  render () {
    const {visible, onClose} = this.props;
    return (
      <Modal
        onCancel={onClose}
        title="Launch commands"
        visible={visible}
        width="50%"
        footer={false}
      >
        <Tabs>
          <Tabs.TabPane key="CLI" tab="CLI">
            {this.renderCLICommand()}
          </Tabs.TabPane>
          <Tabs.TabPane key="API" tab="API">
            {this.renderAPICommand()}
          </Tabs.TabPane>
        </Tabs>
      </Modal>
    );
  }
}

export default inject('preferences')(observer(LaunchCommand));
