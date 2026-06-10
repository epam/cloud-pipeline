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
import classNames from 'classnames';
import {Table, Row, Col, Spin} from 'antd';
import {observer} from 'mobx-react';
import styles from './ClusterNode.module.css';
import displayDate from '../../utils/displayDate';

@observer
export default class ClusterNodeGeneralInfo extends Component {
  state = {dataLoaded: false};

  generateCapacityTable(node, isLoading) {
    const columns = [
      {
        dataIndex: 'key',
        key: 'key',
        title: '',
        className: styles.keyCell,
      },
      {
        dataIndex: 'allocatable',
        key: 'allocatable',
        title: 'Allocatable',
        className: styles.valueCell,
      },
      {
        dataIndex: 'capacity',
        key: 'capacity',
        title: 'Capacity',
        className: styles.valueCell,
      },
    ];
    const keys = [];
    const table = [];
    for (const key in node.allocatable) {
      if (Object.hasOwn(node.allocatable, key) && keys.indexOf(key) === -1) {
        keys.push(key);
      }
    }
    for (const key in node.capacity) {
      if (Object.hasOwn(node.capacity, key) && keys.indexOf(key) === -1) {
        keys.push(key);
      }
    }
    for (let i = 0; i < keys.length; i++) {
      table.push({
        key: keys[i],
        allocatable: node.allocatable[keys[i]],
        capacity: node.capacity[keys[i]],
      });
    }
    return this.generateTable({table, columns, key: 'key', showHeader: true, isLoading});
  }

  generateAddressesTable(node, isLoading) {
    const columns = [
      {
        dataIndex: 'type',
        key: 'type',
        title: 'Type',
        className: styles.keyCell,
      },
      {
        dataIndex: 'address',
        key: 'address',
        title: 'Address',
        className: styles.valueCell,
      },
    ];
    return this.generateTable({
      table: node.addresses.map((a) => a),
      columns,
      key: 'type',
      title: 'Addresses',
      showHeader: false,
      isLoading,
    });
  }

  generateKeyValueTable(obj, title, isLoading) {
    const columns = [
      {
        dataIndex: 'key',
        key: 'key',
        title: 'Key',
        className: styles.keyCell,
      },
      {
        dataIndex: 'value',
        key: 'value',
        title: 'Value',
        className: styles.valueCell,
      },
    ];
    const table = [];
    for (const key in obj) {
      if (Object.hasOwn(obj, key)) {
        table.push({
          key,
          value: obj[key],
        });
      }
    }
    return this.generateTable({table, columns, key: 'key', title, showHeader: false, isLoading});
  }

  generateTable(tableData) {
    const title = tableData.title
      ? () => <span className={styles.tableTitle}>{tableData.title}</span>
      : undefined;
    return (
      <Table
        className={classNames(styles.table, {[styles.cloudNodeTable]: this.props.isCloudNode})}
        style={{margin: '0 5px'}}
        columns={tableData.columns}
        dataSource={tableData.table}
        title={title}
        rowKey={tableData.key}
        showHeader={tableData.showHeader}
        pagination={false}
        loading={tableData.isLoading}
        rowClassName={() => 'cp-even-odd-element'}
        size="small"
      />
    );
  }

  componentDidUpdate() {
    if (!this.state.dataLoaded && !this.props.node.pending) {
      this.setState({dataLoaded: true});
    }
  }

  renderSystemInfoRow = () => {
    const addressesTable = this.generateAddressesTable(
      this.props.node.value,
      this.props.node.pending,
    );
    const labelsTable = this.generateKeyValueTable(
      this.props.node.value.labels,
      'Labels',
      this.props.node.pending,
    );
    const systemInfoTable = this.generateKeyValueTable(
      this.props.node.value.systemInfo,
      'System info',
      this.props.node.pending,
    );
    if (this.props.isCloudNode) {
      return (
        <div style={{display: 'flex', gap: 5}}>
          {addressesTable}
          {labelsTable}
        </div>
      );
    }
    return (
      <Row key="system info">
        <Col span={11}>{systemInfoTable}</Col>
        <Col span={8}>
          {addressesTable} <br />
          {labelsTable}
        </Col>
      </Row>
    );
  };

  render() {
    const {isCloudNode} = this.props;
    if (this.props.node.error) {
      return null;
    }
    if (!this.state.dataLoaded && this.props.node.pending) {
      return (
        <Row type="flex" justify="center">
          <Spin />
        </Row>
      );
    } else {
      const node = this.props.node.value;
      const allocatableAndCapacityTable = this.generateCapacityTable(node, this.props.node.pending);
      return (
        <div style={{overflowY: 'auto'}}>
          <Row key="main info">
            <Col>
              <span className={styles.mainInfoPart}>
                <b>Created:</b> {displayDate(node.creationTimestamp)}
              </span>
            </Col>
          </Row>
          <br />
          {this.renderSystemInfoRow()}
          <br />
          {!isCloudNode ? (
            <Row key="allocatable and capacity table">
              <Col span={11}>{allocatableAndCapacityTable}</Col>
            </Row>
          ) : null}
        </div>
      );
    }
  }
}
