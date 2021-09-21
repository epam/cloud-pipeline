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

import React, {Component} from 'react';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Button, Checkbox, Input, Modal, Row, Table} from 'antd';
import {SensitiveBucketsWarning} from '../../../runs/actions';
import styles from './Browser.css';
import {CP_CAP_LIMIT_MOUNTS} from '../form/utilities/parameters';

function sensitiveSorter (a, b) {
  return a.sensitive - b.sensitive;
}

function arraysAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const sA = new Set(a);
  const sB = new Set(b);
  if (sA.size !== sB.size) {
    return false;
  }
  for (let aa of sA) {
    if (!sB.has(aa)) {
      return false;
    }
  }
  return true;
}

export function filterNFSStorages (nfsSensitivePolicy, sensitiveStoragesAreSelected) {
  return a => !sensitiveStoragesAreSelected ||
    !/^skip$/i.test(nfsSensitivePolicy) ||
    a.type !== 'NFS';
}

@inject('preferences')
@observer
export default class AvailableStoragesBrowser extends Component {
  static propTypes = {
    visible: PropTypes.bool,
    availableStorages: PropTypes.array,
    selectedStorages: PropTypes.array,
    onCancel: PropTypes.func,
    onSave: PropTypes.func
  };

  state = {
    selectedStorages: [],
    searchString: null
  };

  componentDidMount () {
    this.updateSelectionFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!arraysAreEqual(prevProps.selectedStorages, this.props.selectedStorages)) {
      this.updateSelectionFromProps();
    }
    if (this.props.visible !== prevProps.visible) {
      this.setState({searchString: null});
    }
  }

  updateSelectionFromProps = () => {
    this.setState({
      selectedStorages: this.props.selectedStorages.slice()
    });
  };

  @computed
  get nfsSensitivePolicy () {
    const {preferences} = this.props;
    return preferences.nfsSensitivePolicy;
  }

  get limitMountsParameter () {
    if (this.state.selectedStorages.length === 0) {
      return {
        [CP_CAP_LIMIT_MOUNTS]: {
          value: 'None'
        }
      };
    }
    if (this.onlyNonSensitiveStoragesAreSelected) {
      return {};
    }
    return {
      [CP_CAP_LIMIT_MOUNTS]: {
        value: this.state.selectedStorages.join(',')
      }
    };
  }

  get availableNonSensitiveStorages () {
    return (this.props.availableStorages || []).filter(s => !s.sensitive);
  }

  get onlyNonSensitiveStoragesAreSelected () {
    const {selectedStorages} = this.state;
    const ids = new Set(selectedStorages.map(id => +id));
    return ids.size === this.availableNonSensitiveStorages.length &&
      this.allAvailableNonSensitiveStoragesAreSelected;
  }

  get allAvailableNonSensitiveStoragesAreSelected () {
    const {selectedStorages} = this.state;
    const ids = new Set(selectedStorages.map(id => +id));
    return this.availableNonSensitiveStorages
      .filter(s => ids.has(+s.id))
      .length === this.availableNonSensitiveStorages.length;
  }

  get allStoragesAreSelected () {
    const {selectedStorages} = this.state;
    const ids = new Set(selectedStorages.map(id => +id));
    const allAllowedStorages = (this.props.availableStorages || [])
      .filter(filterNFSStorages(this.nfsSensitivePolicy, this.hasSelectedSensitiveStorages));
    return allAllowedStorages
      .filter(s => ids.has(+s.id))
      .length === allAllowedStorages.length;
  }

  get hasSelectedSensitiveStorages () {
    const {selectedStorages} = this.state;
    const ids = new Set(selectedStorages.map(id => +id));
    return (this.props.availableStorages || [])
      .filter(s => s.sensitive && ids.has(+s.id))
      .length > 0;
  }

  onSave = () => {
    if (this.props.onSave) {
      this.props.onSave(this.selectedStorages);
    }
  };

  onCancel = () => {
    if (this.props.onCancel) {
      this.props.onCancel();
    }
  };

  onSearch = (event) => {
    const searchString = event.target.value;
    this.setState({searchString});
  };

  onSelect = (event, storage) => {
    event.stopPropagation();
    const selectedStorageId = +(storage.id);
    const {selectedStorages} = this.state;
    const existingIndex = selectedStorages.indexOf(selectedStorageId);
    if (existingIndex >= 0) {
      selectedStorages.splice(existingIndex, 1);
    } else {
      selectedStorages.push(selectedStorageId);
    }
    this.setState({selectedStorages});
  };

  selectAllNonSensitive = () => {
    this.setState({
      selectedStorages: this.availableNonSensitiveStorages.map(s => +(s.id)),
      searchString: null
    });
  };

  selectAll = () => {
    const hasSensitive = (this.props.availableStorages || []).find(a => a.sensitive);
    this.setState({
      selectedStorages: (this.props.availableStorages || [])
        .filter(filterNFSStorages(this.nfsSensitivePolicy, hasSensitive))
        .map(s => +(s.id)),
      searchString: null
    });
  };

  clearSelection = () => {
    this.setState({selectedStorages: []});
  };

  itemIsSelected = (item) => {
    return this.state.selectedStorages &&
      this.state.selectedStorages.filter(p => p === +(item.id)).length > 0;
  };

  get availableStorages () {
    if (!this.props.availableStorages || !this.props.availableStorages.length) {
      return [];
    }

    const storageMatches = (storage) => {
      if (!this.state.searchString || !this.state.searchString.length) {
        return true;
      }
      return storage.name.toLowerCase().includes(this.state.searchString.toLowerCase()) ||
        storage.type.toLowerCase().includes(this.state.searchString.toLowerCase()) ||
        (storage.description &&
          storage.description.toLowerCase().includes(this.state.searchString.toLowerCase()));
    };

    return this.props.availableStorages
      .filter(filterNFSStorages(this.nfsSensitivePolicy, this.hasSelectedSensitiveStorages))
      .filter(storageMatches)
      .sort(sensitiveSorter);
  }

  get selectedStorages () {
    const {selectedStorages} = this.state;
    const ids = new Set(selectedStorages.map(s => +s));
    const hasSensitive = (this.props.availableStorages || [])
      .find(s => s.sensitive && ids.has(+s.id));
    const allAllowedStorages = (this.props.availableStorages || [])
      .filter(filterNFSStorages(this.nfsSensitivePolicy, !!hasSensitive));
    const allowedIds = new Set(allAllowedStorages.map(s => +s.id));
    return selectedStorages.filter(s => allowedIds.has(+s));
  }

  renderStoragesTable = () => {
    const columns = [
      {
        key: 'selection',
        title: '',
        className: styles.checkboxCell,
        render: (item) => {
          return (
            <Checkbox
              checked={this.itemIsSelected(item)}
              onChange={(e) => this.onSelect(e, item)} />
          );
        }
      }, {
        title: 'Name',
        dataIndex: 'name',
        key: 'name',
        render: (name, storage) => {
          return (
            <Row>
              <Row style={storage.sensitive ? {color: 'red'} : {}}>
                {name}
                {
                  storage.sensitive && (
                    <span
                      style={{
                        fontSize: 'smaller',
                        marginLeft: 5,
                        padding: '0 2px',
                        border: '1px solid #ae1726',
                        borderRadius: 2
                      }}
                    >
                      SENSITIVE
                    </span>
                  )
                }
              </Row>
              <Row style={{fontSize: 'smaller'}}>
                {storage.pathMask}<span style={{marginLeft: 5}}>{storage.description}</span>
              </Row>
            </Row>
          );
        }
      }, {
        title: 'Type',
        dataIndex: 'type',
        key: 'type'
      }
    ];

    return (
      <Row type="flex" style={{flex: 1, overflow: 'auto'}}>
        <Table
          className={styles.table}
          dataSource={this.availableStorages}
          columns={columns}
          rowKey="name"
          pagination={false}
          style={{width: '100%'}}
          locale={{emptyText: 'No data storages available'}}
          size="small" />
      </Row>
    );
  };

  render () {
    return (
      <Modal
        width="80%"
        title="Select data storages to limit mounts"
        visible={this.props.visible}
        onCancel={this.onCancel}
        footer={
          <Row type="flex" justify="end">
            <Button onClick={this.onCancel}>
              Cancel
            </Button>
            <Button
              type="primary"
              onClick={this.onSave}
            >
              OK{
                !!this.selectedStorages.length > 0 &&
                ` (${this.selectedStorages.length})`
              }
            </Button>
          </Row>
        }>
        <div style={{maxHeight: '60vh', display: 'flex', flexDirection: 'column'}}>
          <Row type="flex" align="middle" style={{marginBottom: 10}}>
            <Input.Search
              style={{flex: 1}}
              value={this.state.searchString}
              placeholder="Search for the storage"
              onChange={this.onSearch}
            />
            <Button
              style={{marginLeft: 5}}
              disabled={this.allAvailableNonSensitiveStoragesAreSelected}
              onClick={this.selectAllNonSensitive}>
              Select all non-sensitive
            </Button>
            <Button
              style={{marginLeft: 5}}
              disabled={this.allStoragesAreSelected}
              onClick={this.selectAll}
            >
              Select all
            </Button>
            {
              this.state.selectedStorages.length &&
              <Button
                type="danger"
                style={{marginLeft: 5}}
                onClick={this.clearSelection}>
                Clear selection
              </Button>
            }
          </Row>
          <SensitiveBucketsWarning
            parameters={this.limitMountsParameter}
            style={{margin: '5px 0'}}
            message={(
              <div>
                Selection contains <b>sensitive storages</b>.
                This will apply a number of restrictions for the job: no Internet access,
                all the storages will be available in a read-only mode,
                you won't be able to extract the data from the running job and other.
              </div>
            )}
          />
          {this.renderStoragesTable()}
        </div>
      </Modal>
    );
  }
}
