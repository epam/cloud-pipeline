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

import styles from './Browser.css';

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

  onSave = () => {
    if (this.props.onSave) {
      this.props.onSave(
        this.state.selectedStorages.length === this.props.availableStorages.length
          ? null : this.getStoragesIds(this.state.selectedStorages)
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

    const alreadySelected = this.state.selectedStorages
      .filter(s => s.name === storage.name).length > 0;
    const selectedStorages = this.state.selectedStorages;
    if (alreadySelected) {
      selectedStorages.splice(selectedStorages.indexOf(storage), 1);
    } else {
      selectedStorages.push(storage);
    }
    this.setState({selectedStorages});
  };

  selectAll = () => {
    this.setState({selectedStorages: this.props.availableStorages, searchString: null});
  };

  clearSelection = () => {
    this.setState({selectedStorages: []});
  };

  itemIsSelected = (item) => {
    return this.state.selectedStorages &&
      this.state.selectedStorages.filter(p => p.name === item.name).length > 0;
  };

  getStoragesIds = (storages) => {
    return (storages || []).map(storage => storage.id);
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

    return this.props.availableStorages.filter(storageMatches);
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
              <Row>
                {name}
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
