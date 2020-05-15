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
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Button, Checkbox, Input, Modal, Row, Table} from 'antd';
import {SensitiveBucketsWarning} from '../../../runs/actions';
import styles from './Browser.css';

export const LIMIT_MOUNTS_PARAMETER = 'CP_CAP_LIMIT_MOUNTS';

function sensitiveSorter (a, b) {
  return a.sensitive - b.sensitive;
}

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

  get limitMountsParameter () {
    return {
      [LIMIT_MOUNTS_PARAMETER]: {
        value: this.state.selectedStorages.join(',')
      }
    };
  }

  get availableNonSensitiveStorages () {
    return (this.props.availableStorages || []).filter(s => !s.sensitive);
  }

  get allAvailableNonSensitiveStoragesAreSelected () {
    const {selectedStorages} = this.state;
    const ids = selectedStorages.map(id => +id);
    return this.availableNonSensitiveStorages
      .filter(s => ids.indexOf(+s.id) >= 0)
      .length === this.availableNonSensitiveStorages.length;
  }

  onSave = () => {
    if (this.props.onSave) {
      this.props.onSave(
        this.allAvailableNonSensitiveStoragesAreSelected ? null : this.state.selectedStorages
      );
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
    this.setState({
      selectedStorages: (this.props.availableStorages || []).map(s => +(s.id)),
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

  @computed
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

    return this.props.availableStorages.filter(storageMatches).sort(sensitiveSorter);
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
      <Row type="flex" style={{height: 450, overflow: 'auto'}}>
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
              disabled={!this.state.selectedStorages.length}
              onClick={this.onSave}>
              OK{
                !!this.state.selectedStorages.length > 0 &&
                ` (${this.state.selectedStorages.length})`
              }
            </Button>
          </Row>
        }>
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
            disabled={this.state.selectedStorages.length === this.props.availableStorages.length}
            onClick={this.selectAll}>
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
              Selection contains <b>sensitive mounts</b>.
            </div>
          )}
        />
        {this.renderStoragesTable()}
      </Modal>
    );
  }

  componentWillReceiveProps (nextProps) {
    let selectedStorages = [];
    if (nextProps.selectedStorages.length) {
      selectedStorages = nextProps.selectedStorages.slice();
    }
    this.setState({
      selectedStorages, searchString: null
    });
  }
}
