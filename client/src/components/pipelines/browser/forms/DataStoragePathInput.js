/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {observer, inject} from 'mobx-react';
import {Button, Dropdown, Input, Row, Tooltip} from 'antd';
import AWSRegionTag from '../../../special/AWSRegionTag';

import styles from './DataStoragePathInput.css';
import LoadingView from '../../../special/LoadingView';

@inject('preferences')
@observer
export default class DataStoragePathInput extends React.Component {

  static propTypes = {
    value: PropTypes.string,
    onChange: PropTypes.func,
    disabled: PropTypes.bool,
    isNfs: PropTypes.bool,
    isNew: PropTypes.bool,
    addExistingStorageFlag: PropTypes.bool,
    awsRegions: PropTypes.array,
    visible: PropTypes.bool,
    onPressEnter: PropTypes.func,
    onValidation: PropTypes.func
  };

  state = {};

  @computed
  get storageObjectPrefix () {
    if (this.props.preferences.loaded) {
      return this.props.preferences.getPreferenceValue('storage.object.prefix');
    }

    return null;
  }

  @computed
  get awsRegions () {
    return this.props.awsRegions;
  }

  @computed
  get efsHostsList () {
    if (!this.awsRegions) {
      return [];
    }
    const regionsEfsHosts = [];
    this.awsRegions.forEach(region => {
      if (region.efsHosts && region.efsHosts.length) {
        region.efsHosts.forEach(efsHost => {
          if (efsHost.trim()) {
            regionsEfsHosts.push({
              regionId: region.regionId,
              regionName: region.name,
              efsName: efsHost
            });
          }
        });
      }
    });
    return regionsEfsHosts;
  }

  @computed
  get currentEfsHost () {
    return this.state.efsHost
      ? this.efsHostsList.filter(r => r.efsName === this.state.efsHost)[0]
      : null;
  }

  onSelectEfsHost = (efsHost) => {
    if (this.state.efsHost !== efsHost) {
      this.setState({efsHost}, this.handleOnChange);
    } else {
      this.handleOnChange();
    }
  };

  onPathChanged = (storagePath) => {
    this.setState({storagePath}, this.handleOnChange);
  };

  validatePath = () => {
    if (!this.currentEfsHost) {
      this.props.onValidation && this.props.onValidation(false);
    } else {
      const valid = this.state.storagePath && this.state.storagePath.startsWith('/');
      this.props.onValidation && this.props.onValidation(valid);
    }
  };

  getValue = () => {
    if (this.props.isNfs && this.state.efsHost && this.state.storagePath) {
      return `${this.state.efsHost}:${this.state.storagePath}`;
    } else if (!this.props.isNfs && this.state.storagePath) {
      return `${this.state.storagePath}`;
    } else {
      return null;
    }
  };

  handleOnChange = () => {
    if (!this.props.onChange || (this.props.isNfs && !this.state.efsHost)) {
      return;
    }
    this.props.onChange(this.getValue());
    this.validatePath();
  };

  displayEfsHostName = (efsHost) => {
    return (
      <Tooltip title={`${efsHost.regionName}: ${efsHost.efsName}`}>
        <Row type="flex" style={{
          flexFlow: 'nowrap'
        }}>
          <AWSRegionTag regionUID={efsHost.regionId} displayName />
          <div style={{
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            textAlign: 'left'
          }}>: {efsHost.efsName}</div>
        </Row>
      </Tooltip>
    );
  };

  renderS3 = () => {
    if (this.props.isNew && !this.props.addExistingStorageFlag) {
      if (this.props.preferences.pending) {
        return <LoadingView />;
      } else if (this.storageObjectPrefix) {
        return (
          <Row type="flex">
            <div
              style={{
                backgroundColor: '#eee',
                border: '1px solid #ccc',
                borderRadius: '4px 0px 0px 4px',
                height: 32,
                maxWidth: '50%'
              }}>
              <span style={{padding: '0 7px'}}>{this.storageObjectPrefix}</span>
            </div>
            <Input
              id="edit-storage-storage-path-input"
              className={styles.pathInput}
              disabled={this.props.disabled}
              ref={this.initializeNameInput}
              size="large"
              value={this.state.storagePath}
              onBlur={this.validatePath}
              onPressEnter={(e) => this.props.onPressEnter && this.props.onPressEnter(e)}
              style={{
                width: 200,
                flex: 1,
                borderRadius: '0px 4px 4px 0px',
                marginLeft: -1
              }}
              onChange={e => this.onPathChanged(e.target.value)} />
          </Row>
        );
      }
    }

    return (
      <Input
        id="edit-storage-storage-path-input"
        ref={!this.props.disabled ? this.initializeNameInput : null}
        onPressEnter={(e) => this.props.onPressEnter && this.props.onPressEnter(e)}
        value={this.state.storagePath}
        onChange={e => this.onPathChanged(e.target.value)}
        disabled={this.props.disabled} />
    );
  };

  renderNFS = () => {
    return (
      <Row type="flex">
        <div
          style={{
            backgroundColor: '#eee',
            border: '1px solid #ccc',
            borderRadius: '4px 0px 0px 4px',
            height: 32,
            maxWidth: '50%'
          }}>
          {
            this.props.disabled || this.efsHostsList
              .filter(
                r => !this.currentEfsHost || r.efsName !== this.currentEfsHost.efsName
              ).length === 0
              ? <Button
                id="edit-storage-storage-path-nfs-mount"
                size="small"
                style={{
                  border: 'none',
                  fontWeight: 'bold',
                  backgroundColor: 'transparent',
                  width: '100%'
                }}
                onClick={null}>
                {
                  this.currentEfsHost
                    ? this.displayEfsHostName(this.currentEfsHost)
                    : (this.state.value && this.state.value.split(':')[0]) ||
                    'Unknown NFS Mount target'
                }
              </Button>
              : <Dropdown
                id="edit-storage-storage-path-nfs-mount"
                overlay={
                  <div className={styles.navigationDropdownContainer}>
                    {
                      this.efsHostsList
                        .filter(
                          r => !this.currentEfsHost || r.efsName !== this.currentEfsHost.efsName
                        )
                        .map(efsHost => {
                          return (
                            <Row key={`${efsHost.regionId}_${efsHost.efsName}`} type="flex">
                              <Button
                                style={{textAlign: 'left', width: '100%', border: 'none'}}
                                onClick={() => this.onSelectEfsHost(efsHost.efsName)}>
                                {this.displayEfsHostName(efsHost)}
                              </Button>
                            </Row>
                          );
                        })
                    }
                  </div>
                }>
                <Button
                  size="small"
                  style={{
                    border: 'none',
                    fontWeight: 'bold',
                    backgroundColor: 'transparent',
                    width: '100%'
                  }}>
                  {
                    this.currentEfsHost
                      ? this.displayEfsHostName(this.currentEfsHost)
                      : (this.state.value && this.state.value.split(':')[0]) ||
                      'Unknown NFS Mount target'
                  }
                </Button>
              </Dropdown>
          }
        </div>
        <Input
          id="edit-storage-storage-path-input"
          className={styles.pathInput}
          disabled={this.props.disabled}
          ref={this.initializeNameInput}
          size="large"
          value={this.state.storagePath}
          onBlur={this.validatePath}
          onPressEnter={(e) => this.props.onPressEnter && this.props.onPressEnter(e)}
          style={{
            width: 200,
            flex: 1,
            borderRadius: '0px 4px 4px 0px',
            marginLeft: -1
          }}
          onChange={e => this.onPathChanged(e.target.value)} />
      </Row>
    );
  };

  render () {
    if (!this.props.isNfs) {
      return this.renderS3();
    }
    if (this.state.value === undefined) {
      return <Input disabled size="large" />;
    }
    return this.renderNFS();
  }

  updateState = (props) => {
    props = props || this.props;
    if (!props.isNfs && props.value && props.value.length && this.state.value !== props.value) {
      this.setState({
        value: props.value,
        storagePath: props.value
      });
    } else if (props.value && props.value.length && this.state.value !== props.value) {
      const parts = props.value.split(':');
      if (parts.length === 2) {
        const [efsHost, storagePath] = parts;
        this.setState({
          value: props.value,
          efsHost,
          storagePath
        });
      }
    } else if (!props.value || !props.value.length) {
      const newState = {
        value: null,
        storagePath: null
      };
      if (!this.state.efsHost) {
        newState.efsHost = props.isNfs && this.efsHostsList && this.efsHostsList.length
          ? this.efsHostsList[0].efsName
          : null;
      }
      this.setState(newState);
    }
  };

  componentWillReceiveProps (nextProps) {
    this.updateState(nextProps);
  }

  componentDidMount () {
    this.updateState();
    this.focusNameInput();
  }

  initializeNameInput = (input) => {
    if (input && input.refs && input.refs.input) {
      this.nameInput = input.refs.input;
      this.nameInput.onfocus = function () {
        setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
      this.focusNameInput();
    }
  };

  focusNameInput = () => {
    if (this.props.visible && this.nameInput) {
      setTimeout(() => {
        this.nameInput.focus();
      }, 0);
    }
  };

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
      if (!this.props.visible) {
        this.setState({
          efsHost: null
        });
      }
    }
  }

}
