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
import {inject, observer} from 'mobx-react';
import {Alert, Button, Input, Modal} from 'antd';
import classNames from 'classnames';
import LoadToolHistory from '../../../../models/tools/LoadToolHistory';
import LoadingView from '../../../special/LoadingView';
import displaySize from '../../../../utils/displaySize';
import styles from './history.css';
import BashCode from '../../../special/bash-code';
import GenerateDockerfile from '../../../../models/tools/GenerateDockerfile';

@inject((stores, {params}) => {
  return {
    toolId: params.id,
    version: params.version,
    history: new LoadToolHistory(params.id, params.version)
  };
})
@observer
export default class History extends React.Component {
  state = {
    selectedLayer: 0,
    generateDockerfileModalVisible: false,
    dockerFileFrom: ''
  };

  onSelectLayer = (index) => {
    this.setState({selectedLayer: index});
  };

  openGenerateDockerfileModal = () => {
    this.setState({generateDockerfileModalVisible: true, dockerFileFrom: ''});
  };

  closeGenerateDockerfileModal = () => {
    this.setState({generateDockerfileModalVisible: false});
  };

  onChangeDockerfileFrom = (event) => {
    this.setState({dockerFileFrom: event.target.value});
  };

  onGenerateDockerfile = () => {
    const {dockerFileFrom} = this.state;
    const {toolId, version} = this.props;
    const url = GenerateDockerfile.url(toolId, version, dockerFileFrom);
    window.open(url, '_blank');
    window.focus();
    this.closeGenerateDockerfileModal();
  }

  renderGenerateDockerfileModal = () => {
    const {
      generateDockerfileModalVisible,
      dockerFileFrom
    } = this.state;
    return (
      <Modal
        visible={generateDockerfileModalVisible}
        onCancel={this.closeGenerateDockerfileModal}
        title="Dockerfile"
        footer={(
          <div className={styles.generateDockerfileFooter}>
            <Button onClick={this.closeGenerateDockerfileModal}>
              CANCEL
            </Button>
            <Button
              type="primary"
              disabled={!dockerFileFrom || dockerFileFrom.trim().length === 0}
              onClick={this.onGenerateDockerfile}
            >
              GENERATE
            </Button>
          </div>
        )}
      >
        <div>
          <Alert
            type="info"
            message={(
              <div style={{textAlign: 'justify'}}>
                Please specify a docker image name, which is going to be used as a base layer to generate a Dockerfile. This name will be used in the "FROM" instruction.
              </div>
            )}
          />
        </div>
        <div className={styles.generateDockerfileRow}>
          <span className={styles.generateDockerfileTitle}>From:</span>
          <Input
            className={styles.generateDockerfileInput}
            value={dockerFileFrom}
            onChange={this.onChangeDockerfileFrom}
          />
        </div>
      </Modal>
    );
  }

  render () {
    if (!this.props.history.loaded && this.props.history.pending) {
      return <LoadingView />;
    }
    if (this.props.history.error) {
      return <Alert type="error" message={this.props.history.error} />;
    }
    const selectedLayer = (this.props.history.value || [])[this.state.selectedLayer];
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <div style={{marginBottom: 5}}>
          <Button type="primary" onClick={this.openGenerateDockerfileModal}>
            Generate Dockerfile
          </Button>
          {this.renderGenerateDockerfileModal()}
        </div>
        <div style={{display: 'flex', flexDirection: 'row', flex: 1}}>
          <div style={{flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto'}}>
            {
              (this.props.history.value || []).map((layer, index) => (
                <div
                  key={`layer-${index}`}
                  className={
                    classNames(
                      styles.layer,
                      'cp-settings-sidebar-element',
                      {'cp-table-element-selected': this.state.selectedLayer === index}
                    )
                  }
                  onClick={() => this.onSelectLayer(index)}
                >
                  <span className={styles.index}>{index + 1}.</span>
                  <code className={styles.command}>{layer.command}</code>
                  <span className={styles.size}>{displaySize(layer.size)}</span>
                </div>
              ))
            }
          </div>
          <div style={{flex: 1, paddingLeft: 5}}>
            {
              selectedLayer && (
                <BashCode
                  className={styles.code}
                  code={(selectedLayer.command || '').trim()}
                />
              )
            }
          </div>
        </div>
      </div>
    );
  }
}
