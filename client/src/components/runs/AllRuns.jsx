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
import {makeObservable} from 'mobx';
import {Alert, Card, Menu, message, Popover, Row} from 'antd';
import {downloadBlob} from '../../utils/download-blob';
import classNames from 'classnames';
import {Link} from 'react-router-dom';
import RunTable, {Columns} from './run-table';
import PipelineRunExport from '../../models/pipelines/PipelineRunExport';
import {getFiltersPayload} from '../../models/pipelines/pipeline-runs-filter';
import checkBlob from '../../utils/check-blob';
import SessionStorageWrapper from '../special/SessionStorageWrapper';
import roleModel from '../../utils/roleModel';
import parseQueryParameters from '../../utils/queryParameters';
import LoadingView from '../special/LoadingView.tsx';
import {RunCountDefault} from '../../models/pipelines/RunCount';
import continuousFetch from '../../utils/continuous-fetch';
import RunsFilterDescription from './run-table/runs-filter-description';
import RunsInfo from './runs-info';
import ActiveRunsFilterDescription from './runs-info/filter-description';
import styles from './AllRuns.module.css';

const getStatusForServer = (active) =>
  active ? ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'] : ['SUCCESS', 'FAILURE', 'STOPPED'];

const DEFAULT_ACTIVE_FILTERS = {
  key: 'active',
  title: 'Active Runs',
  filters: {
    statuses: getStatusForServer(true),
    onlyMasterJobs: true,
  },
  autoUpdate: true,
  showCount: true,
  showPersonalRuns: true,
};

const DEFAULT_COMPLETED_FILTERS = {
  key: 'completed',
  title: 'Completed Runs',
  filters: {
    statuses: getStatusForServer(false),
    onlyMasterJobs: true,
  },
  autoUpdate: false,
  showPersonalRuns: false,
};

const CHARTS_INFO_TAB = 'info';
const CHARTS_INFO_DETAILS = 'details';

@roleModel.authenticationInfo
@inject('counter', 'preferences')
@inject(({routing}) => {
  const {status = 'active'} = routing.params;
  const query = parseQueryParameters(routing);
  const all = Object.hasOwn(query, 'all') && /^(true|undefined)$/i.test(`${query.all}`);
  return {
    status,
    all,
    routing,
  };
})
@observer
class AllRuns extends React.Component {
  state = {
    counters: {},
    details: undefined,
    chartsFilters: undefined,
    exportPending: false,
  };

  countersManagementToken = 0;
  counters = {};

  constructor(props) {
    super(props);
    makeObservable(this, {});
  }

  componentDidMount() {
    this.manageCounters();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (
      prevProps.status !== this.props.status &&
      this.props.status !== CHARTS_INFO_TAB &&
      this.props.status !== CHARTS_INFO_DETAILS
    ) {
      this.clearFilters();
    }
  }

  componentWillUnmount() {
    this.countersManagementToken += 1;
    this.stopCounters();
  }

  clearFilters = () => {
    this.setState({
      chartsFilters: undefined,
    });
  };

  manageCounters = async () => {
    this.countersManagementToken += 1;
    this.counters = {};
    const token = this.countersManagementToken;
    const {counter: globalCounter, preferences} = this.props;
    try {
      await preferences.fetchIfNeededOrWait();
      if (token !== this.countersManagementToken) {
        return;
      }
      const filters = this.uiRunsFilters.filter(
        (aFilter) => aFilter.showCount || aFilter.showPersonalRuns,
      );
      for (const filter of filters) {
        const request = new RunCountDefault(globalCounter, filter.filters);
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
              [filter.key]: request.runsCount,
            },
          });
        };
        const counter = continuousFetch({
          fetchImmediate: true,
          call,
          afterInvoke: after,
          intervalMS: 10000,
        });
        this.counters[filter.key] = {
          counter,
          request,
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

  get uiRunsFilters() {
    const {preferences} = this.props;
    let runsFilters = [];
    if (preferences.loaded) {
      runsFilters = (preferences.uiRunsFilters || []).slice();
    }
    if (!runsFilters.find((filter) => filter.key === 'active')) {
      runsFilters = [DEFAULT_ACTIVE_FILTERS, ...runsFilters];
    }
    if (!runsFilters.find((filter) => filter.key === 'completed')) {
      runsFilters = [...runsFilters, DEFAULT_COMPLETED_FILTERS];
    }
    return runsFilters;
  }

  get runsInfoChartsAvailable() {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo.loaded) {
      return authenticatedUserInfo.value.admin;
    }
    return false;
  }

  get currentFilters() {
    const {status} = this.props;
    const filters = this.uiRunsFilters;
    return filters.find((aFilter) => aFilter.key.toLowerCase() === (status || '').toLowerCase());
  }

  get additionalFilters() {
    return (this.currentFilters || {}).additionalFilters;
  }

  navigateToRuns = (status, my = false) => {
    SessionStorageWrapper.setItem(SessionStorageWrapper.ACTIVE_RUNS_KEY, my);
    SessionStorageWrapper.navigateToRuns(this.props.routing, status);
  };

  onChangeRunTableFilters = (filters) => {
    const {runTableFilters} = this.state;
    if (runTableFilters === filters) {
      return;
    }
    this.setState({runTableFilters: filters});
  };

  getTableFilters = () => {
    const current = this.currentFilters;
    if (!current) {
      return null;
    }
    const {all, authenticatedUserInfo} = this.props;
    const owner =
      authenticatedUserInfo.loaded && authenticatedUserInfo.value
        ? authenticatedUserInfo.value.userName
        : '';
    const signature = `${current.key}|${all}|${owner}`;
    if (this._tableFiltersSignature === signature && this._tableFilters) {
      return this._tableFilters;
    }
    const filters = {
      ...(current.filters || {}),
      onlyMasterJobs: true,
    };
    if (current.showPersonalRuns && !all && authenticatedUserInfo.loaded && owner) {
      filters.owners = [owner];
    }
    this._tableFiltersSignature = signature;
    this._tableFilters = filters;
    return filters;
  };

  buildMenuItems = () => {
    const {status} = this.props;
    const {counters = {}, details} = this.state;
    const isRunsInfoChartsDetailsPage = /^details$/i.test(status) && details;
    const isRunsInfoChartsPage = /^info$/i.test(status) || (/^details$/i.test(status) && !details);
    const signature = [
      this.uiRunsFilters
        .map(
          (filter) =>
            `${filter.key}:${filter.title}:${filter.showCount ? counters[filter.key] || 0 : ''}`,
        )
        .join('|'),
      this.runsInfoChartsAvailable ? '1' : '0',
      isRunsInfoChartsDetailsPage ? '1' : '0',
      isRunsInfoChartsPage ? '1' : '0',
    ].join(';');
    if (this._menuItemsSignature === signature && this._menuItems) {
      return this._menuItems;
    }
    this._menuItemsSignature = signature;
    this._menuItems = [
      ...this.uiRunsFilters.map((filter) => ({
        key: filter.key,
        label: (
          <Popover
            content={<RunsFilterDescription filters={filter.filters} style={{maxWidth: 200}} />}
            trigger={['hover']}
          >
            <Link
              id={`${filter.key}-runs-button`}
              to={SessionStorageWrapper.getRunsLink(filter.key)}
            >
              {filter.title || `${filter.key} runs`}
              {filter.showCount && counters[filter.key] > 0 ? ` (${counters[filter.key]})` : ''}
            </Link>
          </Popover>
        ),
      })),
      ...(this.runsInfoChartsAvailable
        ? [
            {
              key: 'info',
              label: (
                <Link
                  id="runs-info-charts-button"
                  to={SessionStorageWrapper.getRunsLink(CHARTS_INFO_TAB)}
                >
                  Info
                </Link>
              ),
            },
          ]
        : []),
      ...(this.runsInfoChartsAvailable && isRunsInfoChartsDetailsPage
        ? [
            {
              key: 'details',
              label: (
                <Link
                  id="runs-info-charts-details-button"
                  to={SessionStorageWrapper.getRunsLink(CHARTS_INFO_DETAILS)}
                >
                  Details
                </Link>
              ),
            },
          ]
        : []),
    ];
    return this._menuItems;
  };

  renderOwnersSwitch = (total, all, current, counters = {}) => {
    if (!current || !current.showPersonalRuns) {
      return null;
    }
    const description = current.title ? current.title.toLowerCase() : `${current.key} runs`;
    if (all) {
      return (
        <Row style={{marginBottom: 5, padding: 2}}>
          Currently viewing <b>all available {description}</b>.{' '}
          <a className="cp-link" onClick={() => this.navigateToRuns(current.key, true)}>
            View only <b>your {description}</b>
          </a>
        </Row>
      );
    }
    const allRunsCount = counters[current.key] || 0;
    let totalInfo = '';
    if (total > 0 && total < allRunsCount) {
      totalInfo = ` (${total} out of ${allRunsCount})`;
    }
    return (
      <Row style={{marginBottom: 5, padding: 2}}>
        Currently viewing only{' '}
        <b>
          your {description}
          {totalInfo}
        </b>
        .{' '}
        <a className="cp-link" onClick={() => this.navigateToRuns(current.key, false)}>
          View <b>other available {description}</b>
        </a>
      </Row>
    );
  };

  renderTable = () => {
    const {all, authenticatedUserInfo} = this.props;
    const current = this.currentFilters;
    const {counters = {}} = this.state;
    if (!current) {
      return <LoadingView />;
    }
    if (
      current.showPersonalRuns &&
      !all &&
      !authenticatedUserInfo.loaded &&
      authenticatedUserInfo.pending
    ) {
      return <LoadingView />;
    }
    const filters = this.getTableFilters();
    return (
      <RunTable
        additionalFilters={this.additionalFilters}
        filters={filters}
        autoUpdate={current.autoUpdate}
        disableFilters={current.showPersonalRuns && !all ? [Columns.owner] : []}
        beforeTable={({total}) => this.renderOwnersSwitch(total, all, current, counters)}
        onChangeFilters={this.onChangeRunTableFilters}
      />
    );
  };

  renderRunsInfoChartsDetailsTable = () => {
    const {details} = this.state;
    if (!details) {
      return null;
    }
    const filters = {
      ...details,
      onlyMasterJobs: false,
    };
    return (
      <div style={{paddingTop: 5}}>
        <div style={{margin: 5}}>
          <ActiveRunsFilterDescription filters={details} postfix={'. '} />
          <Link to={SessionStorageWrapper.getRunsLink(CHARTS_INFO_TAB)}>
            Back to active runs info
          </Link>
        </div>
        <RunTable filters={filters} autoUpdate={false} disableFilters={[]} />
      </div>
    );
  };

  onRunsChartsApplyFilters = (filters) => {
    const {owners, dockerImages, instanceTypes, tags = [], statuses} = filters || {};
    this.setState({
      details: {
        owners,
        instanceTypes,
        dockerImages,
        tags: Object.fromEntries(tags.map((tag) => [tag, true])),
        statuses,
      },
    });
    this.navigateToRuns(CHARTS_INFO_DETAILS);
  };

  onChangeChartsFilters = (newFilters) => {
    this.setState({
      chartsFilters: newFilters,
    });
  };

  exportRuns = () => {
    const {preferences} = this.props;
    this.setState({exportPending: true}, async () => {
      const hide = message.loading('Exporting runs...');
      try {
        const {runTableFilters = {}} = this.state;
        await preferences.fetchIfNeededOrWait();
        const pageSize = preferences.systemRunFilterMaxPageSize;
        const {filters, userFilters, tags} = runTableFilters;
        const request = new PipelineRunExport();
        const payload = getFiltersPayload({
          ...filters,
          ...userFilters,
          tags,
          page: 1,
          pageSize,
        });
        await request.send(payload);
        if (request.value instanceof Blob) {
          const error = await checkBlob(request.value, 'Error downloading runs');
          if (error) {
            throw new Error(error);
          }
          downloadBlob(request.value, 'pipeline-runs.csv');
        } else {
          throw new Error(request.error || 'Error downloading runs');
        }
      } catch (error) {
        message.error(error.message, 5);
      } finally {
        hide();
        this.setState({
          exportPending: false,
        });
      }
    });
  };

  render() {
    const current = this.currentFilters;
    const {status} = this.props;
    const {details} = this.state;
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
        className={classNames(
          styles.runsCard,
          'cp-panel',
          'cp-panel-no-hover',
          'cp-panel-borderless',
        )}
        styles={{body: {padding: 15}}}
      >
        <div className={styles.headerRow}>
          <Menu
            mode="horizontal"
            selectedKeys={selectedKeys}
            className={styles.tabsMenu}
            items={this.buildMenuItems()}
          />
          <div
            style={{
              textTransform: 'uppercase',
              height: 36,
              lineHeight: '46px',
            }}
          >
            <Link
              id="export-button"
              style={{marginRight: 15}}
              onClick={this.exportRuns}
              disabled={this.state.exportPending}
            >
              Export
            </Link>
            <Link
              id="advanced-runs-filter-button"
              to={'/runs/filter'}
              style={{whiteSpace: 'nowrap'}}
            >
              Advanced filter
            </Link>
          </div>
        </div>
        {!isRunsInfoChartsPage && !isRunsInfoChartsDetailsPage && this.renderTable()}
        {isRunsInfoChartsPage && this.runsInfoChartsAvailable && (
          <RunsInfo
            onApplyFilters={this.onRunsChartsApplyFilters}
            style={{paddingTop: 5}}
            filters={this.state.chartsFilters}
            onFiltersChange={this.onChangeChartsFilters}
          />
        )}
        {isRunsInfoChartsDetailsPage &&
          this.runsInfoChartsAvailable &&
          this.renderRunsInfoChartsDetailsTable()}
        {(isRunsInfoChartsPage || isRunsInfoChartsDetailsPage) && !this.runsInfoChartsAvailable && (
          <Alert title="Access denied" type="warning" />
        )}
      </Card>
    );
  }
}

export default AllRuns;
