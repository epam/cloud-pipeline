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
import {Alert, Badge, Table, Row, Spin} from 'antd';
import {observer} from 'mobx-react';
import styles from './ClusterNode.css';

@observer
export default class ClusterNodePods extends Component {
  state = {dataLoaded: false};

  componentDidUpdate () {
    if (!this.state.dataLoaded && !this.props.node.pending) {
      this.setState({dataLoaded: true});
    }
  }

  summarize (key, array) {
    let value = 0;
    let metrics = null;
    let initialized = false;
    const valueRegex = new RegExp(/^\d+/);
    const metricsRegex = new RegExp(/[a-zA-Z]+$/);
    for (let i = 0; i < array.length; i++) {
      if (array[i] && array[i][key]) {
        const metricsResult = metricsRegex.exec(array[i][key]);
        const valueResult = valueRegex.exec(array[i][key]);
        if (!initialized) {
          initialized = true;
          if (metricsResult) {
            metrics = metricsResult[0];
          }
        } else if ((!metrics && metricsResult) || (metrics && !metricsResult) || (metrics && metricsResult && metrics !== metricsResult[0])) {
          return undefined;
        }
        if (!valueResult) {
          return undefined;
        }
        value += parseInt(valueResult[0]);
      }
    }
    if (!initialized) {
      return undefined;
    }
    if (metrics) {
      return `${value}${metrics}`;
    } else {
      return `${value}`;
    }
  }

  podStatusRenderer = (status) => {
    if (status) {
      let badge = null;
      switch (status.toUpperCase()) {
        case 'RUNNING':
          badge = <Badge status="processing"/>;
          break;
        case 'FAILED':
          badge = <Badge status="error"/>;
          break;
        case 'PENDING':
          badge = <Badge status="warning"/>;
          break;
        case 'SUCCEEDED':
          badge = <Badge status="success"/>;
          break;
        default:
          badge = <Badge status="default"/>;
          break;
      }
      return <span>{badge}{status}</span>;
    }
    return null;
  };

  podContainerStatusRenderer = (status) => {
    if (status) {
      const containerStatus = status.status;
      let statusDescription = containerStatus;
      if (status.reason) {
        statusDescription = `${containerStatus} (${status.reason})`;
      }
      if (containerStatus === null) {
        return <Badge status="default"/>;
      } else {
        let badge = null;
        switch (containerStatus.toUpperCase()) {
          case 'RUNNING':
            badge = <Badge status="processing"/>;
            break;
          case 'TERMINATED':
            if (status.reason && status.reason.toUpperCase() === 'COMPLETED') {
              badge = <Badge status="success"/>;
            } else {
              badge = <Badge status="error"/>;
            }
            break;
          case 'WAITING':
            badge = <Badge status="warning"/>;
            break;
          default:
            badge = <Badge status="default"/>;
            break;
        }
        return <span>{badge}{statusDescription}</span>;
      }
    }
    return null;
  };

  statusRenderer = (status, item) => {
    if (item.isContainer) {
      return this.podContainerStatusRenderer(status);
    }
    return this.podStatusRenderer(status);
  };

  generateNonTerminatedPodsTable(node, isLoading) {
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        render: (text, record) => record.isContainer ? <span><span className={styles.podContainerIndicator}>CONTAINER</span>{text}</span> : text
      },
      {
        dataIndex: 'namespace',
        key: 'namespace',
        title: 'Namespace'
      },
      {
        dataIndex: 'status',
        key: 'status',
        title: 'Status',
        render: this.statusRenderer
      }
    ];
    const dataSource = [];
    const keys = [];

    for (let i = 0; i < node.pods.length; i++) {
      for (let j = 0; j < node.pods[i].containers.length; j++) {
        const container = node.pods[i].containers[j];
        if (container.requests) {
          for (let key in container.requests) {
            if (container.requests.hasOwnProperty(key) && keys.indexOf(key) == -1) {
              keys.push(key);
            }
          }
        }
        if (container.limits) {
          for (let key in container.limits) {
            if (container.limits.hasOwnProperty(key) && keys.indexOf(key) == -1) {
              keys.push(key);
            }
          }
        }
      }
    }

    if (keys.length > 0) {
      const requestsColumn = {
        title: 'Requests',
        children: keys.map(k => {
          return {
            dataIndex: `requests.${k}`,
            key: `requests.${k}`,
            title: k.toUpperCase()
          };
        })
      };
      const limitsColumn = {
        title: 'Limits',
        children: keys.map(k => {
          return {
            dataIndex: `limits.${k}`,
            key: `limits.${k}`,
            title: k.toUpperCase()
          };
        })
      };
      columns.push(requestsColumn);
      columns.push(limitsColumn);
    }

    for (let i = 0; i < node.pods.length; i++) {
      const pod = {
        name: node.pods[i].name,
        namespace: node.pods[i].namespace,
        status: node.pods[i].phase,
        uid: node.pods[i].uid
      };
      const containers = [];
      for (let j = 0; j < node.pods[i].containers.length; j++) {
        const container = node.pods[i].containers[j];
        containers.push({
          name: container.name,
          isContainer: true,
          requests: container.requests,
          limits: container.limits,
          uid: `${pod.uid}-${j}`,
          status: container.status
        });
      }
      const podRequests = {};
      const podLimits = {};
      for (let k = 0; k < keys.length; k++) {
        const req = this.summarize(keys[k], containers.map(c => c.requests));
        if (req) {
          podRequests[keys[k]] = req;
        }
        const lim = this.summarize(keys[k], containers.map(c => c.limits));
        if (lim) {
          podLimits[keys[k]] = lim;
        }
      }
      if (containers.length === 1) {
        pod.children = containers;
        pod.requests = containers[0].requests;
        pod.limits = containers[0].limits;
      } else if (containers.length > 0) {
        pod.children = containers;
        pod.requests = podRequests;
        pod.limits = podLimits;
      }
      dataSource.push(pod);
    }
    return (
      <Table className={styles.table}
             columns={columns}
             dataSource={dataSource}
             rowKey="uid"
             loading={isLoading}
             pagination={{pageSize: 20}}
             bordered={true}
             rowClassName={(row, index) => index % 2 === 0 ? styles.tableRowEven : styles.tableRowOdd}
             size="small"/>
    );
  }

  render() {
    if (!this.state.dataLoaded && this.props.node.pending) {
      return (<Row type="flex" justify="center"><Spin /></Row>);
    } else if (this.props.node.value && !this.props.node.value.pods) {
      return (<Row><Alert message="The node doesn't contain non-terminated pods" type="info"/></Row>);
    }
    else {
      const node = this.props.node.value;
      const table = this.generateNonTerminatedPodsTable(node, this.props.node.pending);
      return (
        <div>
          {table}
        </div>
      );
    }
  }
}
