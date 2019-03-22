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
import {inject, observer} from 'mobx-react';
import {computed, observable} from 'mobx';
import {Button, Icon, Row, Select, Table} from 'antd';
import styles from './Browser.css';
import {ItemTypes} from '../model/treeStructureFunctions';
import FireCloudMethodSnapshotConfigurationsRequest
  from '../../../models/firecloud/FireCloudMethodSnapshotConfigurations';

@inject('googleApi', 'fireCloudMethods')
@observer
export default class FireCloudMethodSnapshotConfigurations extends React.Component {

  static propTypes = {
    namespace: PropTypes.string,
    method: PropTypes.string,
    snapshot: PropTypes.string,
    configuration: PropTypes.string,
    configurationSnapshot: PropTypes.string,
    onConfigurationSelect: PropTypes.func,
    isSelected: PropTypes.bool,
    onCreateNew: PropTypes.func
  };

  @observable
  _configurations = new FireCloudMethodSnapshotConfigurationsRequest(
    this.props.googleApi,
    this.props.namespace,
    this.props.method,
    this.props.snapshot
  );

  @computed
  get currentMethod () {
    if (this.props.fireCloudMethods.loaded) {
      return (this.props.fireCloudMethods.value || [])
        .filter(m => m.name === this.props.method)
        .map(m => ({...m, key: `${m.namespace}_${m.name}`})).shift();
    }
    return null;
  }

  @computed
  get configurations () {
    if (this._configurations.loaded) {
      const configurations = {};
      (this._configurations.value || [])
        .forEach((c, i, arr) => {
          if (!configurations[c.name]) {
            configurations[c.name] = {
              name: c.name,
              namespace: c.namespace,
              key: `${this.currentMethod.name}_${this.props.snapshot}_${c.name}`,
              type: ItemTypes.fireCloudMethodConfiguration,
              selectedSnapshot: this.props.configurationSnapshot || arr[arr.length - 1].snapshotId,
              snapshots: []
            };
          }
          configurations[c.name].snapshots.push(c.snapshotId);
        });
      return Object.values(configurations);
    }
    return [];
  }

  onConfigurationSelect = (configuration) => {
    if (this.props.onConfigurationSelect && configuration.selectedSnapshot) {
      this.props.onConfigurationSelect(configuration.name, configuration.selectedSnapshot);
    }
  };

  onConfigurationSnapshotSelect = (item) => (snapshot) => {
    item.selectedSnapshot = snapshot;
    if (this.props.configuration) {
      this.onConfigurationSelect(item);
    }
  };

  renderTreeItemSelection = (item) => {
    if (this.props.isSelected && item.name === this.props.configuration) {
      return (
        <Row type="flex" justify="end">
          <Icon type="check-circle" />
        </Row>
      );
    }
    return undefined;
  };

  renderTreeItemType = (item) => {
    switch (item.type) {
      case ItemTypes.fireCloudMethodConfiguration:
        return <Icon
          type="setting"
          style={{color: '#2796dd', fontSize: 'larger', lineHeight: 'inherit'}} />;
      default: return <div />;
    }
  };

  renderTreeItemActions = (item) => {
    if (!item.snapshots) {
      return undefined;
    }

    return (
      <Select
        style={{width: '100%'}}
        defaultValue={item.selectedSnapshot}
        onSelect={this.onConfigurationSnapshotSelect(item)}>
        {
          item.snapshots.map(s => {
            return (
              <Select.Option key={s}>{s}</Select.Option>
            );
          })
        }
      </Select>
    );
  };

  columns = [
    {
      key: 'selection',
      className: styles.treeItemSelection,
      render: (item) => this.renderTreeItemSelection(item),
      onCellClick: (item) => this.onConfigurationSelect(item)
    },
    {
      key: 'type',
      className: styles.treeItemType,
      render: (item) => this.renderTreeItemType(item),
      onCellClick: (item) => this.onConfigurationSelect(item)
    },
    {
      dataIndex: 'name',
      key: 'name',
      title: 'Name',
      className: styles.treeItemName,
      onCellClick: (item) => this.onConfigurationSelect(item)
    },
    {
      key: 'actions',
      className: styles.treeItemActions,
      render: (item) => this.renderTreeItemActions(item)
    }
  ];

  render () {
    if (!this.props.fireCloudMethods.pending &&
      !!this._configurations &&
      !this._configurations.pending &&
      this.configurations.length === 0) {
      return (
        <div>
          {
            this.currentMethod && this.currentMethod.synopsis &&
            <Row type="flex">
              {this.currentMethod.synopsis}
            </Row>
          }
          <Row type="flex" justify="center" style={{margin: 10}}>
            <Button
              onClick={this.props.onCreateNew}
              type="primary">
              Create new
            </Button>
          </Row>
        </div>
      );
    }
    return (
      <div>
        {
          this.currentMethod && this.currentMethod.synopsis &&
          <Row type="flex">
            {this.currentMethod.synopsis}
          </Row>
        }
        <Row type="flex" style={{flex: 1, overflowY: 'auto'}}>
          <Table
            key="content"
            rowKey="key"
            size="small"
            loading={
              this.props.fireCloudMethods.pending ||
              (!!this._configurations && this._configurations.pending)
            }
            expandedRowRender={null}
            pagination={false}
            showHeader={false}
            title={null}
            style={{width: '100%'}}
            className={styles.childrenContainer}
            columns={this.columns}
            dataSource={this.configurations}
            rowClassName={(item) => item.key} />
        </Row>
      </div>
    );
  }

  componentDidUpdate (prevProps) {
    if (this.props.method !== prevProps.method || this.props.namespace !== prevProps.namespace ||
      this.props.snapshot !== prevProps.snapshot) {
      this._configurations = new FireCloudMethodSnapshotConfigurationsRequest(
        this.props.googleApi,
        this.props.namespace,
        this.props.method,
        this.props.snapshot);
    }
  }

}
