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
import moment from 'moment-timezone';
import classNames from 'classnames';
import {
  Button,
  Checkbox,
  Input,
  Row,
  Table,
  Tooltip
} from 'antd';
import clusterNodes from '../../../models/cluster/ClusterNodes';
import nodesFilter from '../../../models/cluster/FilterClusterNodes';
import pools from '../../../models/cluster/HotNodePools';
import {inject, observer} from 'mobx-react';
import connect from '../../../utils/connect';
import localization from '../../../utils/localization';
import displayDate from '../../../utils/displayDate';
import parseQueryParameters from '../../../utils/queryParameters';
import {renderNodeLabels} from '../renderers';
import styles from '../Cluster.css';

@connect({
  clusterNodes,
  nodesFilter
})
@localization.localizedComponent
@inject('clusterNodes', 'nodesFilter')
@inject((stores) => {
  const {routing} = stores;
  const query = parseQueryParameters(routing);
  return {
    ...stores,
    pools,
    filter: query
  };
})
@observer
export default class CoreNodesTable extends localization.LocalizedReactComponent {
  state = {
    appliedFilter: {
      haveRunId: null,
      noRunId: null,
      runId: null,
      address: null
    },
    filter: {
      runId: {
        noRunId: null,
        haveRunId: null,
        finalNoRunId: null,
        finalHaveRunId: null,
        visible: false,
        filtered () {
          return (
            this.finalValue !== null ||
            this.finalNoRunId ||
            this.finalHaveRunId
          );
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

  get nodes () {
    const {clusterNodes} = this.props;
    if (clusterNodes.loaded) {
      return (clusterNodes.value || []).filter(node => !node.runId);
    }
    return [];
  }

  get filteredNodes () {
    const {address} = this.state.appliedFilter;
    const matchesAddress = node => node.addresses &&
      node.addresses.map(a => a.address).find(a => a.includes(address));
    const matches = node => (!address || matchesAddress(node));
    return this.nodes.filter(matches);
  }

  refreshTable = async () => {
    const {clusterNodes} = this.props;
    await clusterNodes.fetch();
  };

  isFilterChanged = () => {
    return this.state.filter.runId.finalValue !== this.state.appliedFilter.runId ||
      this.state.filter.address.finalValue !== this.state.appliedFilter.address ||
      this.state.filter.runId.finalNoRunId !== this.state.appliedFilter.noRunId ||
      this.state.filter.runId.finalHaveRunId !== this.state.appliedFilter.haveRunId
    ;
  };

  applyFilter = () => {
    const filter = this.state.appliedFilter;
    filter.runId = this.state.filter.runId.finalValue;
    filter.noRunId = this.state.filter.runId.finalNoRunId;
    filter.haveRunId = this.state.filter.runId.finalHaveRunId;
    filter.address = this.state.filter.address.finalValue;
    this.setState({appliedFilter: filter});
  };

  componentDidMount () {
    this.applyFilter();
  }

  componentDidUpdate () {
    if (this.isFilterChanged()) {
      this.applyFilter();
    }
  }

  onFilterDropdownVisibleChange = (filterParameterName) => (visible) => {
    const filter = this.state.filter;
    filter[filterParameterName].visible = visible;
    if (!visible) {
      filter[filterParameterName].validationError = undefined;
      filter[filterParameterName].value = filter[filterParameterName].finalValue
        ? filter[filterParameterName].finalValue : null;
      if (filterParameterName === 'runId') {
        filter[filterParameterName].noRunId = filter[filterParameterName].finalNoRunId
          ? filter[filterParameterName].finalNoRunId : null;
        filter[filterParameterName].haveRunId = filter[filterParameterName].finalHaveRunId
          ? filter[filterParameterName].finalHaveRunId : null;
      }
    }
    this.setState({filter});
  };

  onFilterChanged = (filterParameterName) => () => {
    const filter = this.state.filter;
    filter[filterParameterName].finalValue = filter[filterParameterName].value;
    if (filterParameterName === 'runId') {
      filter[filterParameterName].finalNoRunId = filter[filterParameterName].noRunId;
      filter[filterParameterName].finalHaveRunId = filter[filterParameterName].haveRunId;
    }
    this.onFilterDropdownVisibleChange(filterParameterName)(false);
  };

  onJobsAssociationFilterChanged = (value, param) => {
    const params = {
      noRunId: 'noRunId',
      haveRunId: 'haveRunId'
    };
    let oppositeParam;
    oppositeParam = (param === params.noRunId)
      ? params.haveRunId
      : params.noRunId;

    const filter = {...this.state.filter};
    filter.runId[param] = value;
    filter.runId[oppositeParam] = value ? !value : value;
    this.setState({filter});
  };

  renderJobsAssociationFilter = () => {
    const options = [
      {title: 'Has run id', prop: 'haveRunId'},
      {title: 'No run id', prop: 'noRunId'}
    ];
    return (
      <div
        className={
          classNames(
            'cp-divider',
            'bottom'
          )
        }
      >
        {
          options.map(({title, prop}) => (
            <div
              key={prop}
              className="cp-filter-popover-item"
            >
              <li className={styles.popoverListItem}>
                <Checkbox
                  checked={this.state.filter.runId[prop]}
                  onChange={(e) => this.onJobsAssociationFilterChanged(e.target.checked, prop)}
                >
                  {title}
                </Checkbox>
              </li>
            </div>
          ))
        }
      </div>
    );
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

  renderLabels = (labels, item, pools) => {
    const {router: {location}} = this.props;
    return renderNodeLabels(
      labels,
      {
        className: styles.nodeLabel,
        location,
        pipelineRun: item.pipelineRun,
        pools
      });
  };

  getInputFilter = (parameter, placeholder) => {
    const isRunId = parameter === 'runId';
    const clear = () => {
      const filter = this.state.filter;
      filter[parameter].value = null;
      filter[parameter].finalValue = null;
      filter[parameter].visible = false;
      filter[parameter].validationError = false;
      if (isRunId) {
        filter[parameter].noRunId = null;
        filter[parameter].haveRunId = null;
      }
      this.setState({filter}, () => {
        this.onFilterChanged(parameter)();
      });
    };

    const onInputChange = (filterParameterName) => (e) => {
      const value = e.target.value;
      const filter = this.state.filter;
      filter[filterParameterName].value = value && value.length > 0 ? value : null;
      if (value && filterParameterName === 'runId') {
        filter[filterParameterName].noRunId = null;
        filter[filterParameterName].haveRunId = null;
      }
      this.setState({filter});
    };

    const validateAndSubmit = () => {
      const filter = this.state.filter;
      let validationSuccedded = false;
      if (isRunId) {
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

    const isError = !!this.state.filter[parameter].validationError;

    const filterDropdown = (
      <div className={classNames(
        styles.filterPopoverContainer,
        'cp-filter-popover-container',
        {
          'cp-error': isError
        }
      )}>
        <ul style={{display: 'flex', flexDirection: 'column'}}>
          <div className={classNames('cp-divider', 'bottom', 'cp-filter-popover-item')}>
            <li className={styles.popoverListItem}>
              <Input
                placeholder={placeholder}
                value={this.state.filter[parameter].value}
                onChange={onInputChange(parameter)}
                onPressEnter={validateAndSubmit}
              />
            </li>
          </div>
          {isRunId && this.renderJobsAssociationFilter()}
        </ul>
        {
          isError && (
            <Row className="cp-error">
              {this.state.filter[parameter].validationError}
            </Row>
          )
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

  generateNodeInstancesTable = (nodes, isLoading, pools) => {
    const onNodeInstanceSelect = (node) => {
      this.props.clusterNodes.clearCachedNode(node.name);
      this.props.router.push(`/cluster/${node.name}`);
    };
    const createdCellContent = (date) => (
      <div className={styles.clusterNodeCellCreated}>
        <span className={styles.clusterNodeContentCreated}>
          {displayDate(date, 'YYYY-MM-DD')}
        </span>
        <span className={styles.clusterNodeContentCreated}>
          {displayDate(date, 'HH:mm:ss')}
        </span>
      </div>
    );
    const addressesCellContent = (addresses) => (
      <div className={styles.clusterNodeCellAddresses}>
        {addresses.map(a => (
          <Tooltip
            placement="topLeft"
            title={a.address}
            key={a.address}
          >
            <span className={styles.clusterNodeContentAddresses}>
              {a.address}
            </span>
          </Tooltip>)
        )}
      </div>
    );
    const alphabeticNameSorter = (a, b) => {
      const nameA = a.name;
      const nameB = b.name;
      if (nameA === nameB) {
        return 0;
      } else if (nameA > nameB) {
        return 1;
      } else {
        return -1;
      }
    };

    const runSorter = (a, b) => {
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

    const dateSorter = (a, b) => {
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
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        sorter: alphabeticNameSorter,
        className: styles.clusterNodeRowName,
        onCellClick: this.onNodeInstanceSelect
      },
      {
        dataIndex: 'pipelineRun',
        key: 'pipelineRun',
        title: this.localizedString('Pipeline'),
        render: pipelineRun => this.renderPipelineName(pipelineRun),
        className: styles.clusterNodeRowPipeline,
        onCellClick: this.onNodeInstanceSelect
      },
      {
        dataIndex: 'labels',
        key: 'labels',
        title: 'Labels',
        render: (labels, item) => this.renderLabels(labels, item, pools),
        ...this.getInputFilter('runId', 'Run Id'),
        sorter: runSorter,
        className: styles.clusterNodeRowLabels,
        onCellClick: this.onNodeInstanceSelect
      },
      {
        dataIndex: 'addresses',
        key: 'addresses',
        title: 'Addresses',
        ...this.getInputFilter('address', 'IP'),
        className: styles.clusterNodeRowAddresses,
        render: (addresses) => addressesCellContent(addresses),
        onCellClick: this.onNodeInstanceSelect
      },
      {
        dataIndex: 'created',
        key: 'created',
        title: 'Created',
        sorter: dateSorter,
        className: styles.clusterNodeRowCreated,
        render: (date) => createdCellContent(date),
        onCellClick: this.onNodeInstanceSelect
      }
    ];
    const dataSource = [];
    for (let i = 0; i < (nodes || []).length; i++) {
      const node = nodes[i];
      dataSource.push({
        name: node.name,
        addresses: node.addresses,
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
        dataSource={dataSource}
        columns={columns}
        rowKey="uid"
        loading={isLoading}
        pagination={{pageSize: 25}}
        rowClassName={(item) => `cluster-row-${item.name}`}
        size="small"
        onRowClick={onNodeInstanceSelect}
      />
    );
  }

  get pending () {
    return this.props.nodesFilter.pending || this.props.clusterNodes.pending;
  }

  render () {
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginBottom: 5
        }}>
          <h3>Core nodes</h3>
          <Button
            id="refresh-cluster-node-button"
            onClick={() => this.refreshTable()}
            disabled={this.pending}
            size="small"
          >
            Refresh
          </Button>
        </div>
        {this.generateNodeInstancesTable(
          this.filteredNodes,
          this.pending,
          this.props.pools.loaded ? (this.props.pools.value || []) : []
        )}
      </div>
    );
  }
};
