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

export function extractFileShareMountList (regions) {
  const list = [];
  for (let i = 0; i < regions.length; i++) {
    const region = regions[i];
    if (region.fileShareMounts && region.fileShareMounts.length) {
      region.fileShareMounts.forEach(fileShareMount => {
        if ((fileShareMount.mountRoot || '').trim()) {
          const separator = fileShareMount.mountType === 'NFS' ? ':' : '';
          const mountPathMask = new RegExp(`^${fileShareMount.mountRoot}${separator}(.*)$`, 'i');
          list.push({
            ...fileShareMount,
            mountPathMask,
            separator,
            regionName: region.name
          });
        }
      });
    }
  }
  return list;
}

export function parseFSMountPath (pathInfo, fileShareMountsList) {
  const [fileShareMountParseResult] = fileShareMountsList.map(fs => {
    const execResult = +fs.id === +pathInfo.fileShareMountId
      ? fs.mountPathMask.exec(pathInfo.path)
      : null;
    return {
      execResult,
      mount: fs
    }
  }).filter(r => !!r.execResult);
  if (fileShareMountParseResult) {
    return {
      fileShareMountId: fileShareMountParseResult.mount.id,
      regionId: fileShareMountParseResult.mount.regionId,
      storagePath: fileShareMountParseResult.execResult[1],
      path: pathInfo.path
    };
  } else {
    return pathInfo;
  }
}

@inject('preferences')
@observer
export class DataStoragePathInput extends React.Component {

  static propTypes = {
    value: PropTypes.object,
    onChange: PropTypes.func,
    disabled: PropTypes.bool,
    isFS: PropTypes.bool,
    isNew: PropTypes.bool,
    addExistingStorageFlag: PropTypes.bool,
    cloudRegions: PropTypes.array,
    visible: PropTypes.bool,
    onPressEnter: PropTypes.func,
    onValidation: PropTypes.func
  };

  state = {
    fileShareMountId: undefined,
    regionId: undefined,
    path: undefined,
    storagePath: undefined
  };

  @computed
  get storageObjectPrefix () {
    if (this.props.preferences.loaded) {
      return this.props.preferences.getPreferenceValue('storage.object.prefix');
    }

    return null;
  }

  @computed
  get cloudRegions () {
    return this.props.cloudRegions;
  }

  @computed
  get fileShareMountsList () {
    if (!this.cloudRegions) {
      return [];
    }
    return extractFileShareMountList(this.cloudRegions);
  }

  @computed
  get currentFileShareMount () {
    return this.state.fileShareMountId
      ? this.fileShareMountsList.filter(r => r.id === this.state.fileShareMountId)[0]
      : null;
  }

  onSelectFileShareMount = (fileShareMount) => {
    if (!this.state.fileShareMountId || this.state.fileShareMountId !== fileShareMount.id) {
      this.setState({fileShareMountId: fileShareMount.id}, this.handleOnChange);
    } else {
      this.handleOnChange();
    }
  };

  onPathChanged = (storagePath) => {
    this.setState({storagePath}, this.handleOnChange);
  };

  validatePath = () => {
    if (!this.currentFileShareMount) {
      this.props.onValidation && this.props.onValidation(false);
    } else {
      const valid = this.state.storagePath && this.state.storagePath.startsWith('/');
      this.props.onValidation && this.props.onValidation(valid);
    }
  };

  getValue = () => {
    if (this.props.isFS && this.currentFileShareMount) {
      return {
        fileShareMountId: this.currentFileShareMount.id,
        regionId: this.currentFileShareMount.regionId,
        path: `${this.currentFileShareMount.mountRoot}${this.currentFileShareMount.separator}${this.state.storagePath || ''}`
      };
    } else {
      return {
        fileShareMountId: undefined,
        regionId: undefined,
        path: this.state.storagePath
      };
    }
  };

  handleOnChange = () => {
    if (!this.props.onChange || (this.props.isFS && !this.currentFileShareMount)) {
      return;
    }
    this.props.onChange(this.getValue());
    this.validatePath();
  };

  displayFileShareHostName = (fileShareMount) => {
    return (
      <Tooltip title={`${fileShareMount.regionName}: ${fileShareMount.mountRoot}`}>
        <Row type="flex" style={{
          flexFlow: 'nowrap'
        }}>
          <AWSRegionTag regionId={fileShareMount.regionId} displayName />
          <div style={{
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            textAlign: 'left'
          }}>: {fileShareMount.mountRoot}</div>
        </Row>
      </Tooltip>
    );
  };

  renderObjectStorage = () => {
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

  renderFS = () => {
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
            this.props.disabled || this.fileShareMountsList
              .filter(
                r => !this.currentFileShareMount || r.id !== this.currentFileShareMount.id
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
                  this.currentFileShareMount
                    ? this.displayFileShareHostName(this.currentFileShareMount)
                    : (this.state.path && this.state.path.split(':')[0]) ||
                    'None'
                }
              </Button>
              : <Dropdown
                id="edit-storage-storage-path-nfs-mount"
                overlay={
                  <div className={styles.navigationDropdownContainer}>
                    {
                      this.fileShareMountsList
                        .filter(
                          r => !this.currentFileShareMount || r.id !== this.currentFileShareMount.id
                        )
                        .map(fileShareMount => {
                          return (
                            <Row key={fileShareMount.id} type="flex">
                              <Button
                                style={{textAlign: 'left', width: '100%', border: 'none'}}
                                onClick={() => this.onSelectFileShareMount(fileShareMount)}>
                                {this.displayFileShareHostName(fileShareMount)}
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
                    this.currentFileShareMount
                      ? this.displayFileShareHostName(this.currentFileShareMount)
                      : (this.state.path && this.state.path.split(':')[0]) ||
                      'None'
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
    if (!this.props.isFS) {
      return this.renderObjectStorage();
    }
    return this.renderFS();
  }

  static storagePathChanged = (oldValue, newValue) => {
    if (!!oldValue !== !!newValue) {
      return true;
    }
    if (!oldValue && !newValue) {
      return false;
    }
    return oldValue.fileShareMountId !== newValue.fileShareMountId ||
      oldValue.regionId !== newValue.regionId ||
      oldValue.path !== newValue.path;
  };

  parseValue = (value) => {
    if (value) {
      const parse = () => {
        if (this.props.isFS) {
          return parseFSMountPath(value, this.fileShareMountsList);
        }
        return {
          fileShareMountId: value.fileShareMountId,
          regionId: value.regionId,
          storagePath: value.path,
          path: value.path
        };
      };
      return parse();
    } else {
      return {
        fileShareMountId: this.fileShareMountsList.length > 0 ? this.fileShareMountsList[0].id : undefined,
        regionId: undefined,
        storagePath: undefined,
        path: undefined
      };
    }
  };

  updateState = (props) => {
    props = props || this.props;
    if (DataStoragePathInput.storagePathChanged(props.value, this.state)) {
      this.setState(this.parseValue(props.value));
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
          fileShareMountId: null
        });
      }
    }
  }
}
