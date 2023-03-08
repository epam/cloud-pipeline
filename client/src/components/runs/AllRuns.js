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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Alert, Card, Col, Menu, Row} from 'antd';
import classNames from 'classnames';
import moment from 'moment-timezone';
import * as styles from './AllRuns.css';
import RunTable from './RunTable';
import SessionStorageWrapper from '../special/SessionStorageWrapper';
import AdaptedLink from '../special/AdaptedLink';
import roleModel from '../../utils/roleModel';
import parseQueryParameters from '../../utils/queryParameters';
import {openReRunForm} from './actions';
import continuousFetch from '../../utils/continuous-fetch';
import PipelineRunSingleFilter from '../../models/pipelines/PipelineRunSingleFilter';

const getStatusForServer = active => active
  ? ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING']
  : ['SUCCESS', 'FAILURE', 'STOPPED'];
const pageSize = 20;
const refreshInterval = 10000;

@roleModel.authenticationInfo
@inject('counter', 'pipelines')
@inject(({routing}, {params}) => {
  const {
    status = 'active'
  } = params;
  const query = parseQueryParameters(routing);
  const all = query.hasOwnProperty('all') && /^(true|undefined)$/i.test(`${query.all}`);
  const active = /^active$/i.test(status);
  return {
    active,
    all
  };
})
@observer
class AllRuns extends React.Component {
  state = {
    page: 1,
    pending: false,
    error: undefined,
    filters: {},
    total: 0,
    runs: []
  };

  @computed
  get pipelines () {
    const {pipelines} = this.props;
    if (pipelines.loaded) {
      return (pipelines.value || []).slice();
    }
    return [];
  }

  componentDidMount () {
    this.resetFilters();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      this.props.active !== prevProps.active ||
      this.props.all !== prevProps.all
    ) {
      this.resetFilters();
    }
  }

  componentWillUnmount () {
    this.stopContinuousRequest();
    this.runTable = undefined;
  }

  stopContinuousRequest = () => {
    if (typeof this.stop === 'function') {
      this.stop();
      this.stop = undefined;
    }
  };

  resetFilters = () => {
    if (this.runTable) {
      this.runTable.clearState();
    }
    this.setState({
      page: 1,
      error: undefined,
      filters: {}
    }, this.fetchPage);
  };

  getFilterParams = async () => {
    const {filters = {}} = this.state;
    const {
      active,
      all,
      authenticatedUserInfo
    } = this.props;
    const params = {
      userModified: false
    };
    if (filters.statuses && filters.statuses.length) {
      params.userModified = true;
      params.statuses = filters.statuses;
    } else {
      params.statuses = getStatusForServer(active);
    }
    const parseDateFilter = filtersDate => {
      if (filtersDate && filtersDate.length === 1) {
        return moment(filtersDate[0]).utc(false).format('YYYY-MM-DD HH:mm:ss.SSS');
      }
      return undefined;
    };
    const startDateFrom = parseDateFilter(filters.started);
    const endDateTo = parseDateFilter(filters.completed);
    if (startDateFrom) {
      params.userModified = true;
      params.startDateFrom = startDateFrom;
    }
    if (endDateTo) {
      params.userModified = true;
      params.endDateTo = endDateTo;
    }
    if (filters.parentRunIds && filters.parentRunIds.length === 1) {
      params.userModified = true;
      params.parentId = filters.parentRunIds[0];
    }
    if (filters.pipelineIds && filters.pipelineIds.length) {
      params.userModified = true;
      params.pipelineIds = filters.pipelineIds;
    }
    if (filters.dockerImages && filters.dockerImages.length) {
      params.userModified = true;
      params.dockerImages = filters.dockerImages;
    }
    if (active && !all) {
      try {
        await authenticatedUserInfo.fetchIfNeededOrWait();
        if (authenticatedUserInfo.loaded && authenticatedUserInfo.value) {
          params.owners = [authenticatedUserInfo.value.userName].filter(Boolean);
        }
      } catch (_) {}
    } else if (filters.owners && filters.owners.length) {
      params.userModified = true;
      params.owners = filters.owners;
    }
    return params;
  }

  fetchPage = () => {
    this.stopContinuousRequest();
    this.setState({pending: true}, async () => {
      const {page} = this.state;
      const {active} = this.props;
      const payload = await this.getFilterParams();
      const requestPayload = {
        ...payload,
        page: page,
        pageSize
      };
      const request = new PipelineRunSingleFilter(
        requestPayload,
        false,
        false
      );
      let token = this.token;
      const call = async () => {
        await request.filter(requestPayload);
        if (request.networkError) {
          throw new Error(request.networkError);
        }
      };
      const before = () => {
        token = this.token = (this.token || 0) + 1;
      };
      const after = () => {
        if (this.token === token) {
          const state = {
            pending: false,
            total: 0,
            runs: [],
            error: undefined
          };
          if (request.loaded) {
            state.total = request.total;
            state.runs = request.value || [];
          } else {
            // error
            state.error = request.error || 'Error fetching runs';
          }
          this.setState(state);
        }
      };
      const {
        stop
      } = continuousFetch({
        continuous: active,
        intervalMS: refreshInterval,
        call,
        afterInvoke: after,
        beforeInvoke: before
      });
      this.stop = stop;
    });
  };

  initializeRunTable = (control) => {
    if (control) {
      this.runTable = control;
    }
  };

  handleTableChange = (pagination, filter) => {
    const {current} = pagination;
    this.setState({
      page: current,
      filters: filter
    }, this.fetchPage);
  };

  launchPipeline = (run) => {
    return openReRunForm(run, this.props);
  };

  onSelectRun = ({id}) => {
    this.props.router.push(`/run/${id}`);
  };

  navigateToActiveRuns = (my = false) => {
    SessionStorageWrapper.setItem(SessionStorageWrapper.ACTIVE_RUNS_KEY, my);
    SessionStorageWrapper.navigateToActiveRuns(this.props.router);
  };

  renderOwnersSwitch = () => {
    const {
      active,
      all,
      counter
    } = this.props;
    const {
      total
    } = this.state;
    if (
      !active ||
      (!all && counter.value <= total)
    ) {
      return null;
    }
    if (all) {
      return (
        <Row style={{marginBottom: 5, padding: 2}}>
          Currently viewing <b>all available active runs</b>.
          {' '}
          <a onClick={() => this.navigateToActiveRuns(true)}>
            View only <b>your active runs</b>
          </a>
        </Row>
      );
    }
    return (
      <Row style={{marginBottom: 5, padding: 2}}>
        Currently viewing only
        {' '}
        <b>
          your active runs ({total} out of {this.props.counter.value})
        </b>.
        {' '}
        <a
          onClick={() => this.navigateToActiveRuns(false)}
        >
          View <b>other available active runs</b>
        </a>
      </Row>
    );
  };

  renderTable = () => {
    const {
      page,
      pending,
      error,
      total,
      runs = []
    } = this.state;
    const {
      active,
      all
    } = this.props;
    if (error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    return (
      <RunTable
        onInitialized={this.initializeRunTable}
        useFilter
        loading={pending}
        dataSource={runs}
        handleTableChange={this.handleTableChange}
        statuses={getStatusForServer(active)}
        pipelines={this.pipelines}
        pagination={{
          total: total,
          pageSize,
          current: page
        }}
        ownersDisabled={active && !all}
        reloadTable={this.fetchPage}
        launchPipeline={this.launchPipeline}
        onSelect={this.onSelectRun}
      />
    );
  };

  render () {
    const {
      active,
      counter
    } = this.props;
    return (
      <Card
        className={
          classNames(
            styles.runsCard,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
        bodyStyle={{padding: 15}}
      >
        <Row type="flex" align="bottom">
          <Col offset={2} span={20}>
            <Row type="flex" justify="center">
              <Menu
                mode="horizontal"
                selectedKeys={active ? ['active'] : ['completed']}
                className={styles.tabsMenu}
              >
                <Menu.Item key="active">
                  <AdaptedLink
                    id="active-runs-button"
                    to={SessionStorageWrapper.getActiveRunsLink()}
                    location={location}>Active Runs
                    {counter.value ? ` (${counter.value})` : ''}</AdaptedLink>
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
        {
          this.renderTable()
        }
      </Card>
    );
  }
}

export default AllRuns;
