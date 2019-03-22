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
import {Table, Row, Col, Spin} from 'antd';
import {observer} from 'mobx-react';
import styles from './ClusterNode.css';
import displayDate from '../../utils/displayDate';

@observer
export default class ClusterNodeGeneralInfo extends Component {

  state = {dataLoaded: false};

  generateAllocatableAndCapacityTable(node, isLoading) {
    const columns = [
      {
        dataIndex: 'key',
        key: 'key',
        title: '',
        className: styles.keyCell
      },
      {
        dataIndex: 'allocatable',
        key: 'allocatable',
        title: 'Allocatable',
        className: styles.valueCell
      },
      {
        dataIndex: 'capacity',
        key: 'capacity',
        title: 'Capacity',
        className: styles.valueCell
      }
    ];
    const keys = [];
    const table = [];
    for (let key in node.allocatable) {
      if (node.allocatable.hasOwnProperty(key) && keys.indexOf(key) === -1) {
        keys.push(key);
      }
    }
    for (let key in node.capacity) {
      if (node.capacity.hasOwnProperty(key) && keys.indexOf(key) === -1) {
        keys.push(key);
      }
    }
    for (let i = 0; i < keys.length; i++) {
      table.push({
        key: keys[i], allocatable: node.allocatable[keys[i]], capacity: node.capacity[keys[i]]
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
        className: styles.keyCell
      },
      {
        dataIndex: 'address',
        key: 'address',
        title: 'Address',
        className: styles.valueCell
      }
    ];
    return this.generateTable({
      table: node.addresses.map(a => a),
      columns,
      key: 'type',
      title: 'Addresses',
      showHeader: false,
      isLoading
    });
  }

  generateKeyValueTable(obj, title, isLoading) {
    const columns = [
      {
        dataIndex: 'key',
        key: 'key',
        title: 'Key',
        className: styles.keyCell
      },
      {
        dataIndex: 'value',
        key: 'value',
        title: 'Value',
        className: styles.valueCell
      }
    ];
    const table = [];
    for (let key in obj) {
      if (obj.hasOwnProperty(key)) {
        table.push({
          key: key,
          value: obj[key]
        });
      }
    }
    return this.generateTable({table, columns, key: 'key', title, showHeader: false, isLoading});
  }

  generateTable (tableData) {
    const title = tableData.title
      ? () => <span className={styles.tableTitle}>{tableData.title}</span>
      : undefined;
    return (
      <Table
        className={styles.table}
        style={{margin: '0 5px'}}
        columns={tableData.columns}
        dataSource={tableData.table}
        title={title}
        rowKey={tableData.key}
        showHeader={tableData.showHeader}
        pagination={false}
        loading={tableData.isLoading}
        rowClassName={(row, index) => index % 2 === 0 ? styles.tableRowEven : styles.tableRowOdd}
        size="small" />
    );
  }

  componentDidUpdate() {
    if (!this.state.dataLoaded && !this.props.node.pending) {
      this.setState({dataLoaded: true});
    }
  }

  render() {
    if (!this.state.dataLoaded && this.props.node.pending) {
      return (<Row type="flex" justify="center"><Spin /></Row>);
    } else {
      const node = this.props.node.value;

      const addressesTable = this.generateAddressesTable(node, this.props.node.pending);
      const labelsTable = this.generateKeyValueTable(node.labels, 'Labels', this.props.node.pending);
      const allocatableAndCapacityTable = this.generateAllocatableAndCapacityTable(node, this.props.node.pending);
      const systemInfoTable = this.generateKeyValueTable(node.systemInfo, 'System info', this.props.node.pending);

      return (
        <div style={{overflowY: 'auto'}}>
          <Row key="main info">
            <Col>
              <span
                className={styles.mainInfoPart}><b>Created:</b> {displayDate(node.creationTimestamp)}</span>
            </Col>
          </Row>
          <br/>
          <Row key="system info">
            <Col span={11}>
              {systemInfoTable}
            </Col>
            <Col span={8}>
              {addressesTable} <br />
              {labelsTable}
            </Col>
          </Row>
          <br/>
          <Row key="allocatable and capacity table">
            <Col span={11}>
              {allocatableAndCapacityTable}
            </Col>
          </Row>
        </div>
      );
    }
  }
}
