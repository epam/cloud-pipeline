/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observable, computed} from 'mobx';
import {observer} from 'mobx-react';
import classNames from 'classnames';
import {
  Select,
  Input,
  Button,
  Icon,
  Table,
  Badge,
  message
} from 'antd';
import CorePods from '../../../models/cluster/CorePods';
import highlightText from '../../special/highlightText';
import styles from '../ClusterNode.css';
import statusRenderer from './utils/status';
import displayDuration from '../../../utils/displayDuration';
import {extractDataset, extractExpandableUids, filterPods} from './utils';
import PodInfoModal from './components/pod-info-modal';
import ContainerLogsModal from './components/container-logs-modal';

@observer
export default class CoreServicesTable extends React.Component {
  _initialFilters = {
    name: '',
    nodeName: undefined,
    status: undefined,
    globalSearch: '',
    namespace: 'default',
    servicesStatus: 'all'
  };

  state = {
    filterDropdownVisible: undefined,
    filters: {...this._initialFilters},
    expandedRows: [],
    podInfoModal: undefined,
    containerLogsModal: undefined
  }

  @observable _pods;
  @observable _pending;
  @observable _globalSearchInputValue = '';

  componentDidMount () {
    this.fetchCorePods();
  }

  @computed
  get pods () {
    if (!this._pods) {
      return [];
    }
    return this._pods;
  }

  @computed
  get filteredPods () {
    const {filters} = this.state;
    return filterPods(this.pods, filters);
  }

  get filteredServices () {
    const {filters} = this.state;
    const getStatusWeight = (status = {}) => {
      if (status.unhealthy > 0) {
        return 2;
      }
      if (status.pending > 0) {
        return 1;
      }
      return 0;
    };
    return extractDataset(this.filteredPods)
      .filter(service => {
        if (filters.servicesStatus === 'healthy') {
          return !service.status.unhealthy && !service.status.pending;
        }
        if (filters.servicesStatus === 'unhealthy') {
          return service.status.unhealthy || service.status.pending;
        }
        return true;
      }).sort((a, b) => {
        const aStatusWeight = getStatusWeight(a.status);
        const bStatusWeight = getStatusWeight(b.status);
        return bStatusWeight - aStatusWeight;
      });
  }

  @computed
  get pending () {
    return this._pending;
  }

  @computed
  get globalSearchInputValue () {
    return this._globalSearchInputValue;
  }

  get filtersApplied () {
    const {filters} = this.state;
    return !!(
      filters.name ||
      filters.nodeName ||
      filters.status ||
      filters.globalSearch ||
      filters.namespace !== this._initialFilters.namespace ||
      filters.servicesStatus !== this._initialFilters.servicesStatus
    );
  }

  fetchCorePods = async () => {
    this._pending = true;
    const request = new CorePods();
    await request.fetch();
    this._pods = request.value;
    if (request.error) {
      this._pending = false;
      return message.error(request.error, 5);
    }
    this._pending = false;
    this.onCollapseExpandedRows();
  };

  setServicesStatus = (status) => this.setState({
    filters: {
      ...this.state.filters,
      servicesStatus: status
    }
  });

  onChangeFilters = (filterKey, value) => {
    const {filters} = this.state;
    this.setState({
      filters: {
        ...filters,
        [filterKey]: value
      }
    }, () => {
      if (value && (filterKey === 'name' || filterKey === 'globalSearch')) {
        this.expandTableRows();
      }
      if (!value && (filterKey === 'name' || filterKey === 'globalSearch')) {
        this.onCollapseExpandedRows();
      }
    });
  };

  hideFilterDropdown = () => {
    this.setState({filterDropdownVisible: undefined});
  };

  onClearFilterField = filterKey => () => {
    const {filters} = this.state;
    this.setState({
      filters: {
        ...filters,
        [filterKey]: this._initialFilters[filterKey]
      }
    }, this.hideFilterDropdown);
  };

  onClearFilters = () => {
    this.onResetGlobalSearchInputValue();
    this.setState(
      {filters: {...this._initialFilters}},
      this.onCollapseExpandedRows
    );
  };

  expandTableRows = () => {
    const keys = extractExpandableUids(this.filteredServices);
    this.setState({expandedRows: keys});
  };

  openInfoModal = (record) => {
    if (record.isContainer) {
      return this.setState({
        containerLogsModal: record
      });
    }
    return this.setState({
      podInfoModal: record
    });
  };

  closeInfoModal = () => this.setState({
    containerLogsModal: undefined,
    podInfoModal: undefined
  });

  onExpandedRowsChanged = (expandedRows) => {
    this.setState({expandedRows});
  };

  onChangeGlobalSearch = (event) => {
    this._globalSearchInputValue = event.target.value;
  };

  onResetGlobalSearchInputValue = () => {
    this._globalSearchInputValue = '';
  };

  onApplyGlobalSearch = (event) => {
    if (event.key && event.key === 'Enter') {
      this.onChangeFilters('globalSearch', this.globalSearchInputValue);
    }
  };

  onCollapseExpandedRows = () => this.setState({expandedRows: []});

  renderTable = () => {
    const {filters, expandedRows} = this.state;
    const {globalSearch} = filters;
    const nodeNames = [...new Set((this.pods || [])
      .map(pod => pod.nodeName))
    ];
    const namespaces = [...new Set((this.pods || [])
      .map(pod => pod.namespace))
    ];
    const getFiltersState = (key) => ({
      filterDropdownVisible: this.state.filterDropdownVisible === key,
      onFilterDropdownVisibleChange: (visible) => {
        this.setState({
          filterDropdownVisible: visible
            ? key
            : undefined
        });
      },
      filtered: filters[key] !== this._initialFilters[key],
      filteredValue: filters[key] !== this._initialFilters[key]
        ? ['filtered']
        : null
    });
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        render: (text, record) => (
          <span>
            {record.isContainer ? (
              <span className={classNames(styles.podContainerIndicator, 'cp-tag')}
              >
                CONTAINER
              </span>
            ) : null}
            {highlightText(text, filters.name || globalSearch)}
            {!record.isService ? (
              <Icon
                onClick={() => this.openInfoModal(record)}
                style={{marginLeft: 5, cursor: 'pointer'}}
                type="info-circle"
              />
            ) : null}
          </span>
        ),
        filterDropdown: (
          <div style={{
            display: 'flex',
            flexDirection: 'column'
          }}>
            <Input
              value={this.state.filters.name}
              onChange={e => this.onChangeFilters('name', e.target.value)}
              placeholder="Name"
              size="small"
              style={{width: 200, margin: 10}}
            />
            <div className={classNames('cp-divider', 'top')} />
            <div style={{
              display: 'flex',
              justifyContent: 'flex-end',
              padding: 10
            }}>
              <a onClick={this.onClearFilterField('name')}>
                Clear
              </a>
            </div>
          </div>
        ),
        ...getFiltersState('name')
      },
      {
        dataIndex: 'nodeName',
        key: 'nodeName',
        title: 'Node name',
        render: (text, record) => <span>{highlightText(text, globalSearch)}</span>,
        filterDropdown: (
          <div style={{
            display: 'flex',
            flexDirection: 'column'
          }}>
            <Select
              value={this.state.filters.nodeName}
              onChange={value => this.onChangeFilters('nodeName', value)}
              style={{minWidth: 300, padding: 10}}
              allowClear
              placeholder="Node name"
            >
              {nodeNames.map(name => (
                <Select.Option
                  key={name}
                  value={name}
                >
                  {name}
                </Select.Option>
              ))}
            </Select>
            <div className={classNames('cp-divider', 'top')} />
            <div style={{
              display: 'flex',
              justifyContent: 'flex-end',
              padding: 10
            }}>
              <a onClick={this.onClearFilterField('nodeName')}>
                Clear
              </a>
            </div>
          </div>
        ),
        ...getFiltersState('nodeName')
      },
      {
        dataIndex: 'namespace',
        key: 'namespace',
        title: 'Namespace',
        render: (text, record) => <span>{highlightText(text, globalSearch)}</span>,
        filterDropdown: (
          <div style={{
            display: 'flex',
            flexDirection: 'column'
          }}>
            <Select
              value={this.state.filters.namespace}
              onChange={value => this.onChangeFilters('namespace', value)}
              style={{minWidth: 300, padding: 10}}
              allowClear
              placeholder="Namespace"
            >
              {namespaces.map(namespace => (
                <Select.Option
                  key={namespace}
                  value={namespace}
                >
                  {namespace}
                </Select.Option>
              ))}
            </Select>
            <div className={classNames('cp-divider', 'top')} />
            <div style={{
              display: 'flex',
              justifyContent: 'flex-end',
              padding: 10
            }}>
              <a onClick={this.onClearFilterField('namespace')}>
                Clear
              </a>
            </div>
          </div>
        ),
        ...getFiltersState('namespace')
      },
      {
        dataIndex: 'status',
        key: 'status',
        title: 'Pod status',
        render: statusRenderer,
        filterDropdown: (
          <div style={{
            display: 'flex',
            flexDirection: 'column'
          }}>
            <Select
              value={this.state.filters.status}
              onChange={value => this.onChangeFilters('status', value)}
              style={{minWidth: 300, padding: 10}}
              allowClear
              placeholder="Pod status"
            >
              {['Running', 'Succeeded', 'Failed', 'Pending'].map(status => (
                <Select.Option
                  key={status}
                  value={status}
                >
                  {status}
                </Select.Option>
              ))}
            </Select>
            <div className={classNames('cp-divider', 'top')} />
            <div style={{
              display: 'flex',
              justifyContent: 'flex-end',
              padding: 10
            }}>
              <a onClick={this.onClearFilterField('status')}>
                Clear
              </a>
            </div>
          </div>

        ),
        ...getFiltersState('status')
      },
      {
        dataIndex: 'restarts',
        key: 'restarts',
        title: 'Restarts',
        render: (text, record) => <span>{text}</span>
      },
      {
        dataIndex: 'uptime',
        key: 'uptime',
        title: 'Uptime',
        render: (text, record) => (
          <span>
            {typeof record.status === 'string' &&
                (record.status || '').toUpperCase() === 'RUNNING'
              ? displayDuration(text)
              : ''}
          </span>
        )
      }
    ];
    return (
      <Table
        loading={this.pending}
        className={styles.table}
        columns={columns}
        dataSource={this.filteredServices}
        rowKey="uid"
        bordered
        pagination={false}
        expandedRowKeys={expandedRows}
        onExpandedRowsChange={this.onExpandedRowsChanged}
        rowClassName={(row, index) => index % 2 === 0
          ? ''
          : 'cp-cluster-node-even-row'
        }
        size="small"
      />
    );
  };

  render () {
    const {filters, podInfoModal, containerLogsModal} = this.state;
    return (
      <div>
        <h3>Services</h3>
        <div style={{
          display: 'flex',
          marginBottom: 5,
          gap: 5,
          alignItems: 'center'
        }}>
          <Button.Group
            size="small"
          >
            <Button
              type={filters.servicesStatus === 'all' ? 'primary' : undefined}
              onClick={() => this.setServicesStatus('all')}
            >
              All
            </Button>
            <Button
              type={filters.servicesStatus === 'healthy' ? 'primary' : undefined}
              onClick={() => this.setServicesStatus('healthy')}
            >
              <Badge status="success" />Healthy
            </Button>
            <Button
              type={filters.servicesStatus === 'unhealthy' ? 'primary' : undefined}
              onClick={() => this.setServicesStatus('unhealthy')}
            >
              <Badge status="error" />Unhealthy
            </Button>
          </Button.Group>
          <Input
            value={this.globalSearchInputValue}
            onChange={this.onChangeGlobalSearch}
            onKeyDown={this.onApplyGlobalSearch}
            placeholder="Search..."
            size="small"
            style={{width: 200}}
          />
          {this.filtersApplied ? (
            <a
              id="reset-core-services-filters"
              onClick={this.onClearFilters}
              disabled={this.pending}
              size="small"
            >
              Clear filters
            </a>) : null}
          <div style={{
            marginLeft: 'auto'
          }}>
            <Button
              id="collapse-table-button"
              onClick={this.onCollapseExpandedRows}
              disabled={this.pending}
              size="small"
              style={{marginRight: 5}}
            >
              Collapse table
            </Button>
            <Button
              id="refresh-core-services-button"
              onClick={this.fetchCorePods}
              disabled={this.pending}
              size="small"
            >
              Refresh
            </Button>
          </div>
        </div>
        {this.renderTable()}
        <PodInfoModal
          pod={podInfoModal}
          onClose={this.closeInfoModal}
        />
        <ContainerLogsModal
          container={containerLogsModal}
          onClose={this.closeInfoModal}
        />
      </div>
    );
  }
};
