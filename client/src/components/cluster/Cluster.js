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
import moment from 'moment';
import {Alert, Button, Card, Col, Input, message, Modal, Row, Table} from 'antd';
import clusterNodes from '../../models/cluster/ClusterNodes';
import nodesFilter from '../../models/cluster/FilterClusterNodes';
import TerminateNodeRequest from '../../models/cluster/TerminateNode';
import displayDate from '../../utils/displayDate';
import {inject, observer} from 'mobx-react';
import connect from '../../utils/connect';
import roleModel from '../../utils/roleModel';
import localization from '../../utils/localization';
import styles from './Cluster.css';
import {renderNodeLabels} from './renderers';

@connect({
  clusterNodes,
  nodesFilter
})
@localization.localizedComponent
@inject('clusterNodes', 'nodesFilter')
@observer
export default class Cluster extends localization.LocalizedReactComponent {

  state = {
    appliedFilter: {
      runId: null,
      address: null
    },
    filter: {
      runId: {
        visible: false,
        filtered () {
          return this.finalValue !== null;
        },
        value: null,
        finalValue: null
      },
      address: {
        visible: false,
        filtered () {
          return this.finalValue !== null;
        },
        value: null,
        finalValue: null
      }
    }
  };

  refreshCluster = () => {
    if (!this.props.nodesFilter.pending) {
      this.props.nodesFilter.send({
        runId: this.state.appliedFilter.runId,
        address: this.state.appliedFilter.address
      });
    }
  };

  isFilterChanged = () => {
    return this.state.filter.runId.finalValue !== this.state.appliedFilter.runId ||
        this.state.filter.address.finalValue !== this.state.appliedFilter.address;
  };

  applyFilter = () => {
    const filter = this.state.appliedFilter;
    filter.runId = this.state.filter.runId.finalValue;
    filter.address = this.state.filter.address.finalValue;
    this.setState({appliedFilter: filter}, () => {
      this.refreshCluster();
    });
  };

  componentDidMount () {
    this.refreshCluster();
  }

  componentDidUpdate () {
    if (this.isFilterChanged()) {
      this.applyFilter();
    }
  }

  renderLabels = (labels, item) => {
    const {router: {location}} = this.props;
    return renderNodeLabels(
      labels,
      {
        className: styles.nodeLabel,
        location,
        pipelineRun: item.pipelineRun
      });
  };

  terminateNode = async(item) => {
    const hide = message.loading('Processing...', 0);
    const request = new TerminateNodeRequest(item.name);
    await request.fetch();
    if (request.error) {
      message.error(request.error, 5);
      hide();
    } else {
      message.destroy();
      await this.refreshCluster();
      hide();
    }
  };

  nodeTerminationConfirm = (item, event) => {
    event.stopPropagation();
    const terminateNode = this.terminateNode;
    Modal.confirm({
      title: `Are you sure you want to terminate '${item.name}' node?`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        (async() => {
          await terminateNode(item);
        })();
      }
    });
  };

  renderPipelineName = (run) => {
    if (run) {
      if (run.pipelineName && run.version) {
        return <span>{run.pipelineName} ({run.version})</span>;
      } else if (run.dockerImage) {
        const parts = run.dockerImage.split('/');
        return parts[parts.length - 1];
      }
    }
    return null;
  };

  renderTerminateButton = (item) => {
    if (roleModel.executeAllowed(item) && roleModel.isOwner(item)) {
      let isMaster = false;
      for (let label in item.labels) {
        if (item.labels.hasOwnProperty(label) &&
          (
            (label.toUpperCase() === 'KUBEADM.ALPHA.KUBERNETES.IO/ROLE' && item.labels[label].toLowerCase() === 'master') ||
            label.toUpperCase() === 'NODE-ROLE.KUBERNETES.IO/MASTER' ||
            (label.toUpperCase() === 'CLOUD-PIPELINE/ROLE' &&
              (
                item.labels[label].toLowerCase() === 'edge' ||
                item.labels[label].toLowerCase() === 'heapster' ||
                item.labels[label].toLowerCase() === 'elasticsearch' ||
                item.labels[label].toLowerCase() === 'master' ||
                item.labels[label].toLowerCase() === 'dns'
              )
            )
          )) {
          isMaster = true;
          break;
        }
      }
      if (!isMaster) {
        return <Button
          id="terminate-node-button"
          type="danger"
          size="small"
          onClick={(event) => this.nodeTerminationConfirm(item, event)}>TERMINATE</Button>;
      }
    }
    return <span />;
  };

  alphabeticNameSorter = (a, b) => {
    const nameA = a.name;
    const nameB = b.name;
    if (nameA === nameB) {
      return 0;
    } else if (nameA > nameB) {
      return 1;
    } else {
      return -1;
    }
  }

  runSorter = (a, b) => {
    const runA = +(a.runId || 0);
    const runB = +(b.runId || 0);
    if (runA === runB) {
      return 0;
    } else if (runA > runB) {
      return 1;
    } else {
      return -1;
    }
  };

  dateSorter = (a, b) => {
    const dateA = moment(a.created);
    const dateB = moment(b.created);
    if (dateA === dateB) {
      return 0;
    } else if (dateA > dateB) {
      return 1;
    } else {
      return -1;
    }
  };

  onFilterDropdownVisibleChange = (filterParameterName) => (visible) => {
    const filter = this.state.filter;
    filter[filterParameterName].visible = visible;
    if (!visible) {
      filter[filterParameterName].validationError = undefined;
      filter[filterParameterName].value = filter[filterParameterName].finalValue
        ? filter[filterParameterName].finalValue : null;
    }
    this.setState({filter});
  };

  onFilterChanged = (filterParameterName) => () => {
    const filter = this.state.filter;
    filter[filterParameterName].visible = false;
    filter[filterParameterName].finalValue = filter[filterParameterName].value;
    this.setState({filter});
  };

  onFilterValueChange = (filterParameterName) => (value) => {
    const filter = this.state.filter;
    filter[filterParameterName].value = value && value.length > 0 ? value : null;
    this.setState({filter});
  };

  onInputChange = (filterParameterName) => (e) => {
    this.onFilterValueChange(filterParameterName)(e.target.value);
  };

  getInputFilter = (parameter, placeholder) => {
    const clear = () => {
      const filter = this.state.filter;
      filter[parameter].value = null;
      filter[parameter].finalValue = null;
      filter[parameter].visible = false;
      filter[parameter].validationError = false;
      this.setState({filter}, () => {
        this.onFilterChanged(parameter);
      });
    };

    const validateAndSubmit = () => {
      const filter = this.state.filter;
      let validationSuccedded = false;
      if (parameter === 'runId') {
        if (!isNaN(filter[parameter].value)) {
          filter[parameter].validationError = undefined;
          validationSuccedded = true;
        } else {
          filter[parameter].validationError = 'Please enter valid integer';
        }
      } else {
        filter[parameter].validationError = undefined;
        validationSuccedded = true;
      }
      this.setState({filter}, () => {
        if (validationSuccedded) {
          this.onFilterChanged(parameter)();
        }
      });
    };

    const validationStyle = () => {
      if (this.state.filter[parameter].validationError) {
        return {
          border: '1px solid red'
        };
      } else {
        return {};
      }
    };

    const filterDropdown = (
      <div className={styles.filterPopoverContainer}>
        <Input
          style={validationStyle()}
          placeholder={placeholder}
          value={this.state.filter[parameter].value}
          onChange={this.onInputChange(parameter)}
          onPressEnter={validateAndSubmit}
        />
        {
          this.state.filter[parameter].validationError !== undefined
          ? (
            <Row style={{color: '#f00'}}>
              {this.state.filter[parameter].validationError}
            </Row>
          ) : undefined
        }
        <Row type="flex" justify="space-between" className={styles.filterActionsButtonsContainer}>
          <a onClick={validateAndSubmit}>OK</a>
          <a onClick={clear}>Clear</a>
        </Row>
      </div>
    );
    return {
      filterDropdown,
      filterDropdownVisible: this.state.filter[parameter].visible,
      filtered: this.state.filter[parameter].filtered(),
      onFilterDropdownVisibleChange: this.onFilterDropdownVisibleChange(parameter),
      filteredValue: this.state.filter[parameter].filtered()
        ? [this.state.filter[parameter].value] : []
    };
  };

  generateNodeInstancesTable (nodes, isLoading) {
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        sorter: this.alphabeticNameSorter,
        className: styles.clusterNodeRowName
      },
      {
        dataIndex: 'pipelineRun',
        key: 'pipelineRun',
        title: this.localizedString('Pipeline'),
        render: pipelineRun => this.renderPipelineName(pipelineRun),
        className: styles.clusterNodeRowPipeline
      },
      {
        dataIndex: 'labels',
        key: 'labels',
        title: 'Labels',
        render: this.renderLabels,
        ...this.getInputFilter('runId', 'Run Id'),
        sorter: this.runSorter,
        className: styles.clusterNodeRowLabels
      },
      {
        dataIndex: 'addresses',
        key: 'addresses',
        title: 'Addresses',
        ...this.getInputFilter('address', 'IP'),
        className: styles.clusterNodeRowAddresses
      },
      {
        dataIndex: 'created',
        key: 'created',
        title: 'Created',
        render: (date) => displayDate(date),
        sorter: this.dateSorter,
        className: styles.clusterNodeRowCreated
      },
      {
        key: 'terminate',
        render: (item) => this.renderTerminateButton(item),
        className: styles.clusterNodeRow
      }
    ];
    const dataSource = [];
    for (let i = 0; i < (nodes || []).length; i++) {
      const node = nodes[i];
      dataSource.push({
        name: node.name,
        addresses: node.addresses.map(a => a.address).join(', '),
        created: node.creationTimestamp,
        labels: node.labels,
        uid: node.uid,
        pipelineRun: node.pipelineRun,
        runId: node.runId,
        mask: node.mask
      });
    }
    return (
      <Table
        className={styles.table}
        dataSource={dataSource}
        columns={columns}
        rowKey="uid"
        loading={isLoading}
        pagination={{pageSize: 25}}
        rowClassName={(item) => `cluster-row-${item.name}`}
        onRowClick={this.onNodeInstanceSelect}
        size="small" />
    );
  }

  onNodeInstanceSelect = (node) => {
    this.props.clusterNodes.clearCachedNode(node.name);
    this.props.router.push(`/cluster/${node.name}`);
  };

  nodeIsSlave = (node) => {
    for (let key in node.labels) {
      if (node.labels.hasOwnProperty(key)) {
        if (key.toUpperCase() === 'NODE-ROLE.KUBERNETES.IO/MASTER') {
          return false;
        } else if (
          key.toUpperCase() === 'KUBEADM.ALPHA.KUBERNETES.IO/ROLE') {
          return node.labels[key].toLowerCase() !== 'master';
        } else if (key.toUpperCase() === 'CLOUD-PIPELINE/ROLE') {
          return (
            node.labels[key].toLowerCase() !== 'edge' &&
            node.labels[key].toLowerCase() !== 'heapster' &&
            node.labels[key].toLowerCase() !== 'elasticsearch' &&
            node.labels[key].toLowerCase() !== 'master' &&
            node.labels[key].toLowerCase() !== 'dns'
          );
        }
      }
    }
    return true;
  };

  render () {
    let total = 1;
    let totalWithRunId = 0;
    let description;
    if (this.props.nodesFilter.loaded) {
      total = this.props.nodesFilter.value.filter(this.nodeIsSlave).length;
      totalWithRunId = this.props.nodesFilter.value.map(n => n).filter(n => n.runId > 0).length;
      if (total > 0) {
        let totalPart = `${total} nodes`;
        if (total === 1) {
          totalPart = `${total} node`;
        }
        if (totalWithRunId > 0) {
          if (totalWithRunId > 1) {
            description = `(${totalPart}, ${totalWithRunId} nodes with associated RunId)`;
          } else {
            description = `(${totalPart}, ${totalWithRunId} node with associated RunId)`;
          }
        } else {
          description = `(${totalPart})`;
        }
      }
    }
    return (
      <Card className={styles.clusterCard} bodyStyle={{padding: 15}}>
        <Row type="flex" align="middle">
          <Col span={22}>
            <span className={styles.nodeMainInfo}>Cluster nodes {description}</span>
          </Col>
          <Col span={2} className={styles.refreshButtonContainer}>
            <Button
              id="cluster-refresh-button"
              onClick={this.refreshCluster}
              disabled={this.props.nodesFilter.pending}>Refresh</Button>
          </Col>
        </Row>
        {
          this.props.nodesFilter.error &&
            <Row>
              <br />
              <Alert
                message={`Error retrieving cluster nodes: ${this.props.nodesFilter.error}`}
                type="error" />
            </Row>
        }
        <br />
        {this.generateNodeInstancesTable(
          this.props.nodesFilter.value,
          this.props.nodesFilter.pending
        )}
      </Card>
    );
  }

}
