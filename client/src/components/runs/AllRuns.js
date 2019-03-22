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
import {inject, observer} from 'mobx-react';
import {Link} from 'react-router';
import {computed, observable} from 'mobx';
import {Card, Col, Menu, Row} from 'antd';
import * as styles from './AllRuns.css';
import RunTable from './RunTable';
import SessionStorageWrapper from '../special/SessionStorageWrapper';
import AdaptedLink from '../special/AdaptedLink';
import pipelineRun from '../../models/pipelines/PipelineRun';
import pipelines from '../../models/pipelines/Pipelines';
import connect from '../../utils/connect';
import roleModel from '../../utils/roleModel';
import parseQueryParameters from '../../utils/queryParameters';
import moment from 'moment';

const getStatusForServer = status => (status === 'active'
  ? ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING']
  : ['SUCCESS', 'FAILURE', 'STOPPED']);
const pageSize = 20;
const refreshInterval = 10000;

@connect({
  pipelineRun
})
@roleModel.authenticationInfo
@inject(({routing, counter, pipelineRun, authenticatedUserInfo}, {params}) => {
  let runFilter;
  let runParams;
  let allUsers = false;
  if (params.status === 'active') {
    const queryParameters = parseQueryParameters(routing);
    allUsers = queryParameters.hasOwnProperty('all')
      ? (queryParameters.all === undefined ? true : queryParameters.all === 'true')
      : false;
    if (!allUsers && authenticatedUserInfo.loaded) {
      runParams = {
        page: 1,
        pageSize: pageSize,
        statuses: getStatusForServer(params.status),
        owners: [authenticatedUserInfo.value.userName],
        userModified: false
      };
      runFilter = pipelineRun.runFilter(runParams, true);
    } else if (allUsers) {
      runParams = {
        page: 1,
        pageSize: pageSize,
        statuses: getStatusForServer(params.status),
        userModified: false
      };
      runFilter = pipelineRun.runFilter(runParams, true);
    }
  } else {
    runParams = {
      page: 1,
      pageSize: pageSize,
      statuses: getStatusForServer(params.status),
      userModified: false
    };
    runFilter = pipelineRun.runFilter(runParams, true);
  }
  return {
    runFilter,
    initialRunParams: runParams,
    counter,
    pipelines: pipelines,
    status: params.status,
    authenticatedUserInfo,
    pipelineRun,
    allUsers
  };
})
@observer
export default class AllRuns extends Component {

  initializeRunFilter = (filterParams) => {
    this._runFilter = this.props.pipelineRun.runFilter(filterParams, true);
    this._initialRunParams = filterParams;
  };
  reloadTable = () => {
    this.runFilter && this.runFilter.fetch();
    this.props.counter && this.props.counter.fetch();
  };

  handleTableChange (pagination, filter) {
    const {current} = pagination;
    this.setState({
      activeRuns: this.state.activeRuns,
      currentPage: current,
      filter
    });
  }

  filterArrayChanged = (oldFilter, newFilter, key) => {
    const oldArray = oldFilter[key] ? oldFilter[key] : [];
    const newArray = newFilter[key] ? newFilter[key] : [];
    if (oldArray.length !== newArray.length) {
      return true;
    } else {
      for (let i = 0; i < oldArray.length; i++) {
        if (newArray.indexOf(oldArray[i]) === -1) {
          return true;
        }
      }
    }
    return false;
  };

  filterStatusesChanged = (oldFilter, newFilter) => {
    return this.filterArrayChanged(oldFilter, newFilter, 'statuses');
  };

  filterPipelinesChanged = (oldFilter, newFilter) => {
    return this.filterArrayChanged(oldFilter, newFilter, 'pipelineIds');
  };

  filterDockerImagesChanged = (oldFilter, newFilter) => {
    return this.filterArrayChanged(oldFilter, newFilter, 'dockerImages');
  };

  filterOwnerChanged = (oldFilter, newFilter) => {
    return this.filterArrayChanged(oldFilter, newFilter, 'owners');
  };

  filterStartDateChanged = (oldFilter, newFilter) => {
    return this.filterArrayChanged(oldFilter, newFilter, 'started');
  };

  filterEndDateChanged = (oldFilter, newFilter) => {
    return this.filterArrayChanged(oldFilter, newFilter, 'completed');
  };

  filterParentIdChanged = (oldFilter, newFilter) => {
    return this.filterArrayChanged(oldFilter, newFilter, 'parentRunIds');
  };

  filterChanged = (oldFilter, newFilter) => {
    return this.filterStatusesChanged(oldFilter, newFilter) ||
      this.filterPipelinesChanged(oldFilter, newFilter) ||
      this.filterDockerImagesChanged(oldFilter, newFilter) ||
      this.filterOwnerChanged(oldFilter, newFilter) ||
      this.filterStartDateChanged(oldFilter, newFilter) ||
      this.filterEndDateChanged(oldFilter, newFilter) ||
      this.filterParentIdChanged(oldFilter, newFilter);
  };

  navigateToActiveRuns = (my = false) => {
    SessionStorageWrapper.setItem(SessionStorageWrapper.ACTIVE_RUNS_KEY, my);
    SessionStorageWrapper.navigateToActiveRuns(this.props.router);
  };

  renderOwnersSwitch = () => {
    if (this.props.status !== 'active') {
      return null;
    }
    if (!this.props.counter.loaded || !this.runFilter || !this.runFilter.loaded) {
      return null;
    }
    if (!this.props.allUsers &&
      this.props.counter.value <= this.runFilter.total) {
      return null;
    }
    if (this.props.allUsers) {
      return (
        <Row style={{marginBottom: 5, padding: 2}}>
          Currently viewing <b>all available active runs</b>. <a onClick={() => this.navigateToActiveRuns(true)}>View only <b>your active runs</b></a>
        </Row>
      );
    } else {
      return (
        <Row style={{marginBottom: 5, padding: 2}}>
          Currently viewing only <b>your active runs ({this.runFilter.total} out of {this.props.counter.value})</b>. <a onClick={() => this.navigateToActiveRuns(false)}>View <b>other available active runs</b></a>
        </Row>
      );
    }
  };

  state = {activeRuns: 0, currentPage: 1, filter: {}};

  initializeRunTable = (control) => {
    if (control) {
      this.runTable = control;
    }
  };
  launchPipeline = ({pipelineId, version, id, configName}) => {
    if (pipelineId && version && id) {
      this.props.router.push(`/launch/${pipelineId}/${version}/${configName || 'default'}/${id}`);
    } else if (pipelineId && version && configName) {
      this.props.router.push(`/launch/${pipelineId}/${version}/${configName}`);
    } else if (pipelineId && version) {
      this.props.router.push(`/launch/${pipelineId}/${version}/default`);
    } else if (id) {
      this.props.router.push(`/launch/${id}`);
    }
  };
  onSelectRun = ({id}) => {
    this.props.router.push(`/run/${id}`);
  };
  refreshTimer;
  startTimer = () => {
    if (!this.refreshTimer) {
      this.refreshTimer = setInterval(this.reloadTable, refreshInterval);
    }
  };
  endTimer = () => {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
  };

  @observable _runFilter;
  @observable _initialRunParams;

  @computed
  get runFilter () {
    return this.props.runFilter || this._runFilter;
  }

  @computed
  get initialRunParams () {
    return this.props.initialRunParams || this._initialRunParams;
  }

  @computed
  get currentRunParams () {
    return {
      statuses: this.state.filter && this.state.filter.statuses && this.state.filter.statuses.length
        ? this.state.filter.statuses
        : getStatusForServer(this.props.status),
      pipelineIds: this.state.filter && this.state.filter.pipelineIds
        ? this.state.filter.pipelineIds
        : undefined,
      dockerImages: this.state.filter && this.state.filter.dockerImages
        ? this.state.filter.dockerImages
        : undefined,
      owners: this.props.status === 'active' && !this.props.allUsers && this.props.authenticatedUserInfo.loaded
        ? [this.props.authenticatedUserInfo.value.userName]
        : (this.state.filter && this.state.filter.owners
          ? this.state.filter.owners
          : undefined),
      started: this.state.filter && this.state.filter.started
        ? this.state.filter.started
        : undefined,
      completed: this.state.filter && this.state.filter.completed
        ? this.state.filter.completed
        : undefined,
      parentRunIds: this.state.filter && this.state.filter.parentRunIds
        ? this.state.filter.parentRunIds
        : undefined
    };
  }

  @computed
  get userModifiedFilter () {
    if (!this.initialRunParams) {
      return false;
    }
    return this.filterStatusesChanged(this.initialRunParams, this.currentRunParams) ||
      this.filterPipelinesChanged(this.initialRunParams, this.currentRunParams) ||
      this.filterDockerImagesChanged(this.initialRunParams, this.currentRunParams) ||
      this.filterOwnerChanged(this.initialRunParams, this.currentRunParams) ||
      this.filterStartDateChanged(this.initialRunParams, this.currentRunParams) ||
      this.filterEndDateChanged(this.initialRunParams, this.currentRunParams) ||
      this.filterParentIdChanged(this.initialRunParams, this.currentRunParams);
  }

  getFilterParams () {
    const statuses = this.state.filter.statuses && this.state.filter.statuses.length
      ? this.state.filter.statuses
      : getStatusForServer(this.props.params.status);
    const startDateFrom = this.state.filter.started && this.state.filter.started.length === 1
      ? moment(this.state.filter.started[0]).utc(false).format('YYYY-MM-DD HH:mm:ss.SSS')
      : undefined;
    const endDateTo = this.state.filter.completed && this.state.filter.completed.length === 1
      ? moment(this.state.filter.completed[0]).utc(false).format('YYYY-MM-DD HH:mm:ss.SSS')
      : undefined;
    const parentId = this.state.filter.parentRunIds && this.state.filter.parentRunIds.length === 1
      ? this.state.filter.parentRunIds[0] : undefined;

    return {
      page: this.state.currentPage,
      pageSize,
      statuses: statuses,
      pipelineIds: this.state.filter.pipelineIds,
      dockerImages: this.state.filter.dockerImages,
      owners: (!this.props.allUsers && this.props.status === 'active' && this.props.authenticatedUserInfo.loaded)
        ? [this.props.authenticatedUserInfo.value.userName]
        : this.state.filter.owners,
      startDateFrom,
      endDateTo,
      parentId,
      userModified: this.userModifiedFilter
    };
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.params.status !== this.props.params.status) {
      this.setState(
        {
          activeRuns: this.props.counter.value,
          currentPage: 1,
          filter: {}
        }, () => {
          if (this.runTable && this.runTable.clearState) {
            this.runTable.clearState();
          }
        }
      );
    }
  }

  componentDidMount () {
    if (this.props.status.toLowerCase() === 'active') {
      this.startTimer();
    } else {
      this.endTimer();
    }
  }

  componentWillUnmount () {
    this.endTimer();
  }

  componentDidUpdate (prevProps, prevState) {
    if ((!this.props.counter.pending && prevState.activeRuns !== this.props.counter.value) ||
      (prevState.currentPage !== this.state.currentPage) ||
      this.filterChanged(prevState.filter, this.state.filter)) {
      this.setState(
        {
          activeRuns: this.props.counter.value,
          status: this.props.params.status
        }
      );
      if (!this.runFilter) {
        this.initializeRunFilter(this.getFilterParams());
      } else {
        this.runFilter.filter(this.getFilterParams(), true);
      }
    }
    if (!this.runFilter && this.props.authenticatedUserInfo.loaded) {
      this.initializeRunFilter(this.getFilterParams());
    }
    if (prevProps.status !== this.props.status) {
      if (this.props.status.toLowerCase() === 'active') {
        this.startTimer();
      } else {
        this.endTimer();
      }
    }
  }

  render () {
    const {status} = this.props.params;

    return (
      <Card className={styles.runsCard} bodyStyle={{padding: 15}}>
        <Row type="flex" align="bottom">
          <Col offset={2} span={20}>
            <Row type="flex" justify="center" className={styles.rowMenu}>
              <Menu mode="horizontal" selectedKeys={[status]} className={styles.tabsMenu}>
                <Menu.Item key="active">
                  <AdaptedLink
                    id="active-runs-button"
                    to={SessionStorageWrapper.getActiveRunsLink()}
                    location={location}>Active Runs
                    {this.props.counter.value ? ` (${this.props.counter.value})` : ''}</AdaptedLink>
                </Menu.Item>
                <Menu.Item key="completed">
                  <AdaptedLink
                    id="completed-runs-button"
                    to={'/runs/completed'}
                    location={location}>Completed Runs</AdaptedLink>
                </Menu.Item>
              </Menu>
            </Row>
          </Col>
          <Col
            span={2}
            type="flex"
            style={{textAlign: 'right', padding: 5, textTransform: 'uppercase'}}>
            <AdaptedLink
              id="advanced-runs-filter-button"
              to={'/runs/filter'}
              location={location}>
              Advanced filter
            </AdaptedLink>
          </Col>
        </Row>
        {
          this.renderOwnersSwitch()
        }
        <Row>
          <RunTable
            onInitialized={this.initializeRunTable}
            useFilter={true}
            loading={this.props.authenticatedUserInfo.pending || !this.runFilter || this.runFilter.pending}
            dataSource={this.runFilter ? this.runFilter.value : []}
            handleTableChange={::this.handleTableChange}
            statuses={getStatusForServer(this.props.params.status)}
            pipelines={this.props.pipelines.pending ? [] : (this.props.pipelines.value || []).map(p => p)}
            pagination={{total: this.runFilter ? this.runFilter.total : 0, pageSize, current: this.state.currentPage}}
            ownersDisabled={this.props.status === 'active' && !this.props.allUsers}
            reloadTable={this.reloadTable}
            launchPipeline={this.launchPipeline}
            onSelect={this.onSelectRun}
          />
        </Row>
      </Card>);
  }
}
