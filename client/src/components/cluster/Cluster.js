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
  Alert,
  Button,
  Checkbox,
  Col,
  AutoComplete,
  message,
  Modal,
  Row,
  Table,
  Tooltip
} from 'antd';
import clusterNodes from '../../models/cluster/ClusterNodes';
import nodesFilter from '../../models/cluster/FilterClusterNodes';
import pools from '../../models/cluster/HotNodePools';
import TerminateNodeRequest from '../../models/cluster/TerminateNode';
import displayDate from '../../utils/displayDate';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import connect from '../../utils/connect';
import roleModel from '../../utils/roleModel';
import localization from '../../utils/localization';
import styles from './Cluster.css';
import {renderNodeLabels} from './renderers';
import parseQueryParameters from '../../utils/queryParameters';
import {
  getRoles,
  nodeRoles,
  testRole,
  parseLabel,
  matchesCloudPipelineRoles,
  containsCPLabel
} from './node-roles';

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
export default class Cluster extends localization.LocalizedReactComponent {
  state = {
    appliedFilter: {
      haveRunId: null,
      noRunId: null,
      hasCloudPipelineRole: null,
      search: null,
      address: null
    },
    filter: {
      labels: {
        haveRunId: null,
        finalHaveRunId: null,
        noRunId: null,
        finalNoRunId: null,
        hasCloudPipelineRole: null,
        finalHasCloudPipelineRole: null,
        visible: false,
        filtered () {
          return (
            this.finalValue !== null ||
            this.finalHaveRunId ||
            this.finalNoRunId ||
            this.finalHasCloudPipelineRole
          );
        },
        value: null,
        finalValue: null,
        searchResult: []
      },
      address: {
        visible: false,
        filtered () {
          return this.finalValue !== null;
        },
        value: null,
        finalValue: null
      }
    },
    selection: []
  };

  @computed
  get currentNodePool () {
    const {filter, pools} = this.props;
    if (filter && filter.pool_id && pools.loaded) {
      return (pools.value || []).find(p => `${p.id}` === `${filter.pool_id}`);
    }
    return undefined;
  }

  get nodes () {
    const {clusterNodes, nodesFilter, filter} = this.props;
    if (filter && Object.keys(filter).length > 0) {
      if (clusterNodes.loaded) {
        const nodes = this.props.clusterNodes.value || [];
        const nodeMatchesLabel = (label) => (node) => node.labels &&
          node.labels.hasOwnProperty(label) &&
          `${node.labels[label] || ''}` === `${filter[label]}`;
        const nodeMatchesLabels = (node) => !Object.keys(filter)
          .find(label => !nodeMatchesLabel(label)(node));
        return (nodes || [])
          .filter(nodeMatchesLabels);
      }
    } else if (nodesFilter.loaded) {
      return nodesFilter.value || [];
    }
    return [];
  }

  get filteredNodes () {
    const {filter} = this.props;
    const {appliedFilter} = this.state;
    const {search, address, haveRunId, hasCloudPipelineRole, noRunId} = appliedFilter;
    if (
      (filter && Object.keys(filter).length > 0) ||
      hasCloudPipelineRole ||
      haveRunId ||
      noRunId ||
      search
    ) {
      const hasRunIdProp = (node) => node.labels && node.labels.hasOwnProperty('runid');
      let matchesSearch;
      if (!isNaN(search)) {
        matchesSearch = (node, value) => hasRunIdProp(node) && `${node.labels.runid}` === `${value}`;
      } else {
        matchesSearch = (node, value) => containsCPLabel(node, value);
      }
      const matchesHaveRunId = node => hasRunIdProp(node) && !isNaN(Number(node.labels.runid));
      const matchesNoRunId = node => (!hasRunIdProp(node) || isNaN(Number(node.labels.runid))) &&
      !matchesCloudPipelineRoles(node);
      const matchesAddress = node => node.addresses &&
        node.addresses.map(a => a.address).find(a => a === address);

      const matches = node =>
        (!search || matchesSearch(node, search)) &&
        (!address || matchesAddress(node)) &&
        (!haveRunId || matchesHaveRunId(node)) &&
        (!noRunId || matchesNoRunId(node)) &&
        (!hasCloudPipelineRole || matchesCloudPipelineRoles(node));

      return this.nodes.filter(matches);
    }
    return this.nodes;
  }

  handleSearch = (value) => {
    const searchResult = new Set();
    let matches;
    if (value) {
      if (isNaN(value)) {
        matches = (node) => node.labels && containsCPLabel(node, value);
        this.nodes
          .filter(matches)
          .forEach(node => {
            Object.keys(node.labels).map(key => {
              const info = parseLabel(key, node.labels[key]);
              if (
                testRole(info.role, nodeRoles.cloudPipelineRole) &&
                info.value.toLowerCase().search(value.toLowerCase()) > -1
              ) {
                searchResult.add(info.value.toUpperCase());
              }
            });
          });
      } else {
        matches = (node) => (
          node.labels &&
          node.labels.hasOwnProperty('runid') &&
          node.labels.runid
        );
        this.nodes
          .filter(matches)
          .map(node => node.labels.runid)
          .forEach(runId => {
            if (runId.search(value) > -1) {
              searchResult.add(runId);
            }
          });
      }
    }
    const {filter} = this.state;
    filter.labels.searchResult = [...searchResult];
    this.setState({filter});
  }

  refreshCluster = () => {
    if (!this.props.clusterNodes.pending) {
      this.props.clusterNodes.fetch();
    }
    if (!this.props.nodesFilter.pending) {
      this.props.nodesFilter.send({
        runId: !isNaN(this.state.appliedFilter.search)
          ? this.state.appliedFilter.search
          : null,
        address: this.state.appliedFilter.address
      });
    }
    this.props.pools.fetch();
  };

  isFilterChanged = () => {
    const {filter, appliedFilter} = this.state;
    return filter.labels.finalValue !== (appliedFilter.search) ||
      filter.address.finalValue !== appliedFilter.address ||
      filter.labels.finalHasCloudPipelineRole !== appliedFilter.hasCloudPipelineRole ||
      filter.labels.finalHaveRunId !== appliedFilter.haveRunId
    ;
  };

  applyFilter = () => {
    const {appliedFilter, filter} = this.state;
    const filterToApply = appliedFilter;
    filterToApply.search = filter.labels.finalValue;
    filterToApply.hasCloudPipelineRole = filter.labels.finalHasCloudPipelineRole;
    filterToApply.haveRunId = filter.labels.finalHaveRunId;
    filterToApply.noRunId = filter.labels.finalNoRunId;
    filterToApply.address = filter.address.finalValue;
    this.setState({appliedFilter: filterToApply}, () => {
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

  terminateNode = async (item) => {
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

  terminateNodes = async () => {
    const hide = message.loading('Terminating...', 0);
    const requests = this.state.selection.map(name => new TerminateNodeRequest(name));
    const promises = requests.map(r => new Promise((resolve) => {
      r.fetch()
        .then(() => {
          resolve(r.message);
        })
        .catch(e => resolve(e.message));
    }));
    Promise.all(promises)
      .then(results => {
        hide();
        const errors = results.filter(Boolean);
        if (errors.length > 0) {
          message.error(
            (
              <div>
                {errors.map(e => (<div>{e}</div>))}
              </div>
            ),
            5
          );
        } else {
          this.refreshCluster();
        }
      })
      .then(() => {
        this.setState({selection: []});
      });
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
        (async () => {
          await terminateNode(item);
        })();
      }
    });
  };

  nodesTerminationConfirm = (event) => {
    const {selection} = this.state;
    event.stopPropagation();
    const terminateNodes = this.terminateNodes;
    const count = selection.length;
    Modal.confirm({
      title:
        `Are you sure you want to terminate ${count} node${count > 1 ? 's' : ''}?`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        (async () => {
          await terminateNodes();
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

  renderJobsAssociationFilter = () => {
    const options = [
      {
        title: 'Has run id',
        prop: 'haveRunId',
        type: 'labels'
      },
      {
        title: 'No run id',
        prop: 'noRunId',
        type: 'labels'
      },
      {
        title: 'Platform core nodes ',
        prop: 'hasCloudPipelineRole',
        type: 'labels'
      }
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
          options.map(({title, prop, type}) => (
            <div
              key={prop}
              className="cp-filter-popover-item"
            >
              <li className={styles.popoverListItem}>
                <Checkbox
                  checked={this.state.filter[type][prop]}
                  onChange={(e) => this.onJobsAssociationFilterChanged(
                    e.target.checked,
                    {prop, type}
                  )}
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

  canTerminateNode = (node) => {
    return roleModel.executeAllowed(node) && roleModel.isOwner(node) && this.nodeIsSlave(node);
  }

  renderTerminateButton = (item) => {
    if (roleModel.executeAllowed(item) && roleModel.isOwner(item) && this.nodeIsSlave(item)) {
      return <Button
        id="terminate-node-button"
        type="danger"
        size="small"
        onClick={(event) => this.nodeTerminationConfirm(item, event)}>TERMINATE</Button>;
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
  };

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
      filter[filterParameterName].value = filter[filterParameterName].finalValue
        ? filter[filterParameterName].finalValue : null;
      if (filterParameterName === 'labels') {
        filter[filterParameterName].noRunId = filter[filterParameterName].finalNoRunId
          ? filter[filterParameterName].finalNoRunId : null;
        filter[filterParameterName].haveRunId = filter[filterParameterName].finalHaveRunId
          ? filter[filterParameterName].finalHaveRunId : null;
      }
    }
    this.setState({filter});
  };

  onFilterChanged = (filterParameterName) => () => {
    const {filter} = this.state;
    filter[filterParameterName].finalValue = filter[filterParameterName].value;
    if (filterParameterName === 'labels') {
      filter[filterParameterName].finalHasCloudPipelineRole = filter[filterParameterName].hasCloudPipelineRole;
      filter[filterParameterName].finalHaveRunId = filter[filterParameterName].haveRunId;
      filter[filterParameterName].finalNoRunId = filter[filterParameterName].noRunId;
    }
    this.onFilterDropdownVisibleChange(filterParameterName)(false);
  };

  onFilterValueChange = (filterParameterName) => (value) => {
    const filter = this.state.filter;
    filter[filterParameterName].value = value && value.length > 0 ? value : null;
    if (value && filterParameterName === 'labels') {
      filter[filterParameterName].hasCloudPipelineRole = null;
      filter[filterParameterName].haveRunId = null;
      filter[filterParameterName].noRunId = null;
    }
    this.setState({filter});
  };

  onInputChange = (filterParameterName) => (label) => {
    this.onFilterValueChange(filterParameterName)(label);
  };

  onJobsAssociationFilterChanged = (value, param) => {
    const {prop, type} = param;
    const options = {
      hasCloudPipelineRole: 'hasCloudPipelineRole',
      haveRunId: 'haveRunId',
      noRunId: 'noRunId'
    };
    const filter = {...this.state.filter};
    Object.keys(options).forEach(key => {
      if (options[key] !== param.prop) {
        filter[type][options[key]] = value ? !value : value;
      } else {
        filter[type][prop] = value;
      }
    });
    this.setState({filter});
  }

  onSearchOptionSelected = (value, parameter) => {
    this.onFilterValueChange(parameter)(value);
    this.onFilterChanged(parameter)();
  }

  getInputFilter = (parameter, placeholder) => {
    const isLabels = parameter === 'labels';
    const {labels} = this.state.filter;
    const clear = () => {
      const filter = this.state.filter;
      filter[parameter].value = null;
      filter[parameter].finalValue = null;
      filter[parameter].visible = false;
      if (isLabels) {
        filter[parameter].hasCloudPipelineRole = null;
        filter[parameter].haveRunId = null;
        filter[parameter].noRunId = null;
      }
      this.setState({filter}, () => {
        this.onFilterChanged(parameter)();
      });
    };
    const Option = AutoComplete.Option;
    const children = labels.searchResult.map((item) => <Option key={item}>{item}</Option>);
    const filterDropdown = (
      <div className={classNames(
        styles.filterPopoverContainer,
        'cp-filter-popover-container'
      )}>
        <ul style={{display: 'flex', flexDirection: 'column'}}>
          <div className={classNames('cp-divider', 'bottom', 'cp-filter-popover-item')}>
            <li className={styles.popoverListItem}>
              <AutoComplete
                value={this.state.filter[parameter].value}
                style={{width: 200}}
                onSearch={this.handleSearch}
                onChange={this.onInputChange(parameter)}
                onSelect={(value) => this.onSearchOptionSelected(value, parameter)}
                placeholder={placeholder}
              >
                {children}
              </AutoComplete>
            </li>
          </div>
          {isLabels && this.renderJobsAssociationFilter()}
        </ul>
        <Row type="flex" justify="space-between" className={styles.filterActionsButtonsContainer}>
          <a onClick={this.onFilterChanged(parameter)}>OK</a>
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
    const {selection = []} = this.state;
    const nodesAllowedToTerminate = nodes
      .filter(node => this.canTerminateNode(node));
    const nodeIsSelected = node => selection.indexOf(node.name) >= 0;
    const toggleNode = node => {
      const index = selection.indexOf(node.name);
      if (index >= 0) {
        selection.splice(index, 1);
      } else {
        selection.push(node.name);
      }
      this.setState({selection});
    };
    const selectAll = (select = true) => {
      if (select) {
        this.setState({
          selection: nodesAllowedToTerminate.map(node => node.name)
        });
      } else {
        this.setState({selection: []});
      }
    };
    const allSelected = nodesAllowedToTerminate.length > 0 &&
      nodesAllowedToTerminate
        .filter(nodeIsSelected)
        .length === nodesAllowedToTerminate.length;
    const indeterminate = nodesAllowedToTerminate.length > 0 &&
      nodes
        .filter(node => this.canTerminateNode(node) && nodeIsSelected(node))
        .length > 0 && !allSelected;
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
    const columns = [
      {
        key: 'selection',
        className: styles.clusterNodeSelection,
        title: (
          <span>
            <Checkbox
              disabled={nodesAllowedToTerminate.length === 0}
              checked={allSelected}
              indeterminate={indeterminate}
              onChange={e => selectAll(e.target.checked)}
            />
          </span>
        ),
        render: (item) => {
          if (this.canTerminateNode(item)) {
            return (
              <Checkbox
                checked={nodeIsSelected(item)}
                onChange={e => toggleNode(item)}
              />
            );
          }
          return null;
        }
      },
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        sorter: this.alphabeticNameSorter,
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
        ...this.getInputFilter('labels', 'Search by Run ID or label'),
        sorter: this.runSorter,
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
        sorter: this.dateSorter,
        className: styles.clusterNodeRowCreated,
        render: (date) => createdCellContent(date),
        onCellClick: this.onNodeInstanceSelect
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
        className={styles.table}
        dataSource={dataSource}
        columns={columns}
        rowKey="uid"
        loading={isLoading}
        pagination={{pageSize: 25}}
        rowClassName={(item) => `cluster-row-${item.name}`}
        size="small"
      />
    );
  }

  onNodeInstanceSelect = (node) => {
    this.props.clusterNodes.clearCachedNode(node.name);
    this.props.router.push(`/cluster/${node.name}`);
  };

  nodeIsSlave = (node) => {
    const roles = getRoles(node.labels);
    return !testRole(roles, nodeRoles.master) && !testRole(roles, nodeRoles.cloudPipelineRole);
  };

  getDescription = () => {
    let total = 1;
    let totalWithRunId = 0;
    let description;
    if (this.filteredNodes.length > 0) {
      total = this.filteredNodes.filter(this.nodeIsSlave).length;
      totalWithRunId = this.filteredNodes
        .filter(n => n.labels && n.labels.runid)
        .length;
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
    return description;
  };

  render () {
    let description = this.getDescription();
    const error = this.props.nodesFilter.error || this.props.clusterNodes.error;
    const selectionLength = (this.state.selection || []).length;
    return (
      <div>
        <Row type="flex" align="middle">
          <Col span={19}>
            <span className={styles.nodeMainInfo}>
              {
                this.currentNodePool
                  ? `${this.currentNodePool.name} nodes `
                  : 'Cluster nodes '
              }
              {description}
            </span>
          </Col>
          <Col span={5} className={styles.refreshButtonContainer}>
            {
              selectionLength > 0 && (
                <Button
                  id="cluster-batch-terminate-button"
                  type="danger"
                  disabled={this.props.nodesFilter.pending || this.props.clusterNodes.pending}
                  style={{marginRight: 5}}
                  onClick={this.nodesTerminationConfirm}
                >
                  Terminate {selectionLength} node{selectionLength > 1 ? 's' : ''}
                </Button>
              )
            }
            <Button
              id="cluster-refresh-button"
              onClick={this.refreshCluster}
              disabled={this.props.nodesFilter.pending || this.props.clusterNodes.pending}>
              Refresh
            </Button>
          </Col>
        </Row>
        {
          error && (
            <Row>
              <br />
              <Alert
                message={`Error retrieving cluster nodes: ${error}`}
                type="error" />
            </Row>
          )
        }
        <br />
        {
          this.generateNodeInstancesTable(
            this.filteredNodes,
            this.props.nodesFilter.pending || this.props.clusterNodes.pending,
            this.props.pools.loaded ? (this.props.pools.value || []) : []
          )
        }
      </div>
    );
  }
};
