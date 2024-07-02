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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Alert, Card, Col, Menu, Popover, Row} from 'antd';
import classNames from 'classnames';
import {Link} from 'react-router';
import RunTable, {Columns} from './run-table';
import SessionStorageWrapper from '../special/SessionStorageWrapper';
import roleModel from '../../utils/roleModel';
import parseQueryParameters from '../../utils/queryParameters';
import LoadingView from '../special/LoadingView';
import {RunCountDefault} from '../../models/pipelines/RunCount';
import continuousFetch from '../../utils/continuous-fetch';
import styles from './AllRuns.css';
import RunsFilterDescription from './run-table/runs-filter-description';
import RunsInfo from './runs-info';
import ActiveRunsFilterDescription from './runs-info/filter-description';

const getStatusForServer = active => active
  ? ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING']
  : ['SUCCESS', 'FAILURE', 'STOPPED'];

const DEFAULT_ACTIVE_FILTERS = {
  key: 'active',
  title: 'Active Runs',
  filters: {
    statuses: getStatusForServer(true),
    onlyMasterJobs: true
  },
  autoUpdate: true,
  showCount: true,
  showPersonalRuns: true
};

const DEFAULT_COMPLETED_FILTERS = {
  key: 'completed',
  title: 'Completed Runs',
  filters: {
    statuses: getStatusForServer(false),
    onlyMasterJobs: true
  },
  autoUpdate: false,
  showPersonalRuns: false
};

const CHARTS_INFO_TAB = 'info';
const CHARTS_INFO_DETAILS = 'details';

@roleModel.authenticationInfo
@inject('counter', 'preferences')
@inject(({routing}, {params}) => {
  const {
    status = 'active'
  } = params;
  const query = parseQueryParameters(routing);
  const all = query.hasOwnProperty('all') && /^(true|undefined)$/i.test(`${query.all}`);
  return {
    status,
    all
  };
})
@observer
class AllRuns extends React.Component {
  state = {
    counters: {},
    details: undefined,
    chartsFilters: undefined
  };

  countersManagementToken = 0;
  counters = {};

  componentDidMount () {
    (this.manageCounters)();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.status !== this.props.status &&
      this.props.status !== CHARTS_INFO_TAB && this.props.status !== CHARTS_INFO_DETAILS) {
      this.clearFilters();
    }
  }

  componentWillUnmount () {
    this.countersManagementToken += 1;
    this.stopCounters();
  }

  clearFilters = () => {
    this.setState({
      chartsFilters: undefined
    });
  };

  manageCounters = async () => {
    this.countersManagementToken += 1;
    this.counters = {};
    const token = this.countersManagementToken;
    const {
      counter: globalCounter,
      preferences
    } = this.props;
    try {
      await preferences.fetchIfNeededOrWait();
      if (token !== this.countersManagementToken) {
        return;
      }
      const filters = this.uiRunsFilters
        .filter((aFilter) => aFilter.showCount || aFilter.showPersonalRuns);
      for (const filter of filters) {
        const request = new RunCountDefault(
          globalCounter,
          filter.filters
        );
        const call = async () => {
          await request.fetch();
          if (request.networkError) {
            throw new Error(request.networkError);
          }
        };
        const after = () => {
          const {counters = {}} = this.state;
          this.setState({
            counters: {
              ...counters,
              [filter.key]: request.runsCount
            }
          });
        };
        const counter = continuousFetch({
          fetchImmediate: true,
          call,
          afterInvoke: after,
          intervalMS: 10000
        });
        this.counters[filter.key] = {
          counter,
          request
        };
      }
    } catch (error) {
      console.warn(error.message);
    }
  };

  stopCounters = () => {
    Object.values(this.counters || {}).forEach(({counter, request}) => {
      if (typeof request.destroy === 'function') {
        request.destroy();
      }
      const {stop} = counter;
      if (typeof stop === 'function') {
        stop();
      }
    });
  };

  @computed
  get uiRunsFilters () {
    const {preferences} = this.props;
    let runsFilters = [];
    if (preferences.loaded) {
      runsFilters = (preferences.uiRunsFilters || []).slice();
    }
    if (!runsFilters.find((filter) => filter.key === 'active')) {
      runsFilters = [
        DEFAULT_ACTIVE_FILTERS,
        ...runsFilters
      ];
    }
    if (!runsFilters.find((filter) => filter.key === 'completed')) {
      runsFilters = [
        ...runsFilters,
        DEFAULT_COMPLETED_FILTERS
      ];
    }
    return runsFilters;
  }

  @computed
  get runsInfoChartsAvailable () {
    const {
      authenticatedUserInfo
    } = this.props;
    if (authenticatedUserInfo.loaded) {
      return authenticatedUserInfo.value.admin;
    }
    return false;
  }

  get currentFilters () {
    const {
      status
    } = this.props;
    const filters = this.uiRunsFilters;
    return filters
      .find((aFilter) => aFilter.key.toLowerCase() === (status || '').toLowerCase());
  }

  get additionalFilters () {
    return (this.currentFilters || {}).additionalFilters;
  }

  navigateToRuns = (status, my = false) => {
    SessionStorageWrapper.setItem(SessionStorageWrapper.ACTIVE_RUNS_KEY, my);
    SessionStorageWrapper.navigateToRuns(this.props.router, status);
  };

  renderOwnersSwitch = (total) => {
    const {
      all
    } = this.props;
    const current = this.currentFilters;
    if (
      !current ||
      !current.showPersonalRuns
    ) {
      return null;
    }
    const description = current.title ? current.title.toLowerCase() : `${current.key} runs`;
    if (all) {
      return (
        <Row style={{marginBottom: 5, padding: 2}}>
          Currently viewing <b>all available {description}</b>.
          {' '}
          <a onClick={() => this.navigateToRuns(current.key, true)}>
            View only <b>your {description}</b>
          </a>
        </Row>
      );
    }
    const {
      counters = {}
    } = this.state;
    const allRunsCount = counters[current.key] || 0;
    let totalInfo = '';
    if (total > 0 && total < allRunsCount) {
      totalInfo = ` (${total} out of ${allRunsCount})`;
    }
    return (
      <Row style={{marginBottom: 5, padding: 2}}>
        Currently viewing only
        {' '}
        <b>
          your {description}
          {totalInfo}
        </b>.
        {' '}
        <a
          onClick={() => this.navigateToRuns(current.key, false)}
        >
          View <b>other available {description}</b>
        </a>
      </Row>
    );
  };

  renderTable = () => {
    const {
      all,
      authenticatedUserInfo
    } = this.props;
    const current = this.currentFilters;
    if (!current) {
      return (
        <LoadingView />
      );
    }
    const filters = {
      ...(current.filters || {}),
      onlyMasterJobs: true
    };
    if (
      current.showPersonalRuns &&
      !all &&
      !authenticatedUserInfo.loaded &&
      authenticatedUserInfo.pending
    ) {
      return (
        <LoadingView />
      );
    }
    if (current.showPersonalRuns && !all && authenticatedUserInfo.loaded) {
      filters.owners = [authenticatedUserInfo.value.userName].filter(Boolean);
    }
    return (
      <RunTable
        additionalFilters={this.additionalFilters}
        filters={filters}
        autoUpdate={current.autoUpdate}
        disableFilters={current.showPersonalRuns && !all ? [Columns.owner] : []}
        beforeTable={({total}) => this.renderOwnersSwitch(total)}
      />
    );
  };

  renderRunsInfoChartsDetailsTable = () => {
    const {
      details
    } = this.state;
    if (!details) {
      return null;
    }
    const filters = {
      ...details,
      onlyMasterJobs: false
    };
    return (
      <div style={{paddingTop: 5}}>
        <div style={{margin: 5}}>
          <ActiveRunsFilterDescription filters={details} postfix={'. '} />
          <Link
            to={SessionStorageWrapper.getRunsLink(CHARTS_INFO_TAB)}
          >
            Back to active runs info
          </Link>
        </div>
        <RunTable
          filters={filters}
          autoUpdate={false}
          disableFilters={[]}
        />
      </div>
    );
  };

  onRunsChartsApplyFilters = (filters) => {
    const {
      owners,
      dockerImages,
      instanceTypes,
      tags = [],
      statuses
    } = filters || {};
    this.setState({details: {
      owners,
      instanceTypes,
      dockerImages,
      tags: Object.fromEntries(tags.map(tag => ([tag, true]))),
      statuses
    }});
    this.navigateToRuns(CHARTS_INFO_DETAILS);
  };

  onChangeChartsFilters = (newFilters) => {
    this.setState({
      chartsFilters: newFilters
    });
  };

  render () {
    const current = this.currentFilters;
    const {
      status
    } = this.props;
    const {
      counters = {},
      details
    } = this.state;
    const isRunsInfoChartsDetailsPage = /^details$/i.test(status) && details;
    const isRunsInfoChartsPage = /^info$/i.test(status) || (/^details$/i.test(status) && !details);
    let selectedKeys = [];
    if (current) {
      selectedKeys = [current.key];
    } else if (this.runsInfoChartsAvailable && isRunsInfoChartsPage) {
      selectedKeys = [CHARTS_INFO_TAB];
    } else if (this.runsInfoChartsAvailable && isRunsInfoChartsDetailsPage) {
      selectedKeys = [CHARTS_INFO_DETAILS];
    }
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
                selectedKeys={selectedKeys}
                className={styles.tabsMenu}
              >
                {
                  this.uiRunsFilters.map((filter) => (
                    <Menu.Item key={filter.key}>
                      <Popover
                        content={(
                          <RunsFilterDescription
                            filters={filter.filters}
                            style={{maxWidth: 200}}
                          />
                        )}
                        trigger={['hover']}
                      >
                        <Link
                          id={`${filter.key}-runs-button`}
                          to={SessionStorageWrapper.getRunsLink(filter.key)}
                        >
                          {filter.title || `${filter.key} runs`}
                          {
                            filter.showCount && counters[filter.key] > 0
                              ? ` (${counters[filter.key]})`
                              : ''
                          }
                        </Link>
                      </Popover>
                    </Menu.Item>
                  ))
                }
                {
                  this.runsInfoChartsAvailable && (
                    <Menu.Item key="info">
                      <Link
                        id={`runs-info-charts-button`}
                        to={SessionStorageWrapper.getRunsLink(CHARTS_INFO_TAB)}
                      >
                        Info
                      </Link>
                    </Menu.Item>
                  )
                }
                {
                  this.runsInfoChartsAvailable && isRunsInfoChartsDetailsPage && (
                    <Menu.Item key="details">
                      <Link
                        id={`runs-info-charts-details-button`}
                        to={SessionStorageWrapper.getRunsLink(CHARTS_INFO_DETAILS)}
                      >
                        Details
                      </Link>
                    </Menu.Item>
                  )
                }
              </Menu>
            </Row>
          </Col>
          <Col
            span={2}
            type="flex"
            style={{textAlign: 'right', padding: 5, textTransform: 'uppercase'}}>
            <Link
              id="advanced-runs-filter-button"
              to={'/runs/filter'}
            >
              Advanced filter
            </Link>
          </Col>
        </Row>
        {
          !isRunsInfoChartsPage && !isRunsInfoChartsDetailsPage && this.renderTable()
        }
        {
          isRunsInfoChartsPage && this.runsInfoChartsAvailable && (
            <RunsInfo
              onApplyFilters={this.onRunsChartsApplyFilters}
              style={{paddingTop: 5}}
              filters={this.state.chartsFilters}
              onFiltersChange={this.onChangeChartsFilters}
            />
          )
        }
        {
          isRunsInfoChartsDetailsPage &&
          this.runsInfoChartsAvailable &&
          this.renderRunsInfoChartsDetailsTable()
        }
        {
          (isRunsInfoChartsPage || isRunsInfoChartsDetailsPage) &&
          !this.runsInfoChartsAvailable && (
            <Alert message="Access denied" type="warning" />
          )
        }
      </Card>
    );
  }
}

export default AllRuns;
