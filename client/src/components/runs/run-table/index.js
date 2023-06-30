/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {isObservableArray, observable, computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import {Alert, Pagination, Table} from 'antd';
import {
  filtersAreEqual,
  getFiltersPayload,
  simpleArraysAreEqual
} from '../../../models/pipelines/pipeline-runs-filter';
import {
  AllColumns,
  Columns,
  ExpandColumn,
  getColumns,
  RUN_LOADING_ERROR_PROPERTY,
  RUN_LOADING_PLACEHOLDER_PROPERTY
} from './columns';
import {AllStatuses} from './statuses';
import continuousFetch from '../../../utils/continuous-fetch';
import PipelineRunSingleFilter from '../../../models/pipelines/PipelineRunSingleFilter';
import PipelineRunSearch from '../../../models/pipelines/PipelineRunSearch';
import {runPipelineActions} from '../actions';
import localization from '../../../utils/localization';
import ChildRuns from './child-runs';
import LoadingView from '../../special/LoadingView';
import styles from './run-table.css';

const DEFAULT_PAGE_SIZE = 20;
const DEFAULT_INTERVAL_SECONDS = 10;

/**
 * @typedef {Object} AutoUpdateProps
 * @property {boolean|function} enabled
 * @property {number} intervalSeconds
 * @property {*} updateToken
 */

/**
 * @param {undefined|boolean|function|AutoUpdateProps} autoUpdate
 * @returns {AutoUpdateProps}
 */
function parseAutoUpdateProps (autoUpdate = false) {
  if (typeof autoUpdate === 'boolean' || typeof autoUpdate === 'function') {
    return {
      updateToken: undefined,
      enabled: autoUpdate,
      intervalSeconds: DEFAULT_INTERVAL_SECONDS
    };
  }
  if (typeof autoUpdate === 'object') {
    const {
      enabled = true,
      intervalSeconds = DEFAULT_INTERVAL_SECONDS,
      updateToken,
      ...rest
    } = autoUpdate;
    return {
      enabled,
      intervalSeconds,
      updateToken,
      ...rest
    };
  }
  return {
    enabled: false,
    intervalSeconds: DEFAULT_INTERVAL_SECONDS,
    updateToken: undefined
  };
}

/**
 * @param {undefined|boolean|AutoUpdateProps} configurationA
 * @param {undefined|boolean|AutoUpdateProps} configurationB
 * @param {boolean} [compareTokens=true]
 * @returns {boolean}
 */
function autoUpdateConfigurationsEqual (
  configurationA,
  configurationB,
  compareTokens = true
) {
  const {
    enabled: enabledA,
    intervalSeconds: intervalSecondsA,
    updateToken: updateTokenA
  } = parseAutoUpdateProps(configurationA);
  const {
    enabled: enabledB,
    intervalSeconds: intervalSecondsB,
    updateToken: updateTokenB
  } = parseAutoUpdateProps(configurationB);
  return enabledA === enabledB &&
    intervalSecondsA === intervalSecondsB &&
    (!compareTokens || updateTokenA === updateTokenB);
}

/**
 * @param {undefined|boolean|string[]} disableFilters
 * @returns {string[]}
 */
function parseDisableFiltersProps (disableFilters = false) {
  if (typeof disableFilters === 'boolean' && disableFilters) {
    return disableFilters ? AllColumns : [];
  }
  if (Array.isArray(disableFilters) || isObservableArray(disableFilters)) {
    return disableFilters.slice();
  }
  return [];
}

/**
 * @param {{id: number, childRunsCount: number, pipelineRunParameters: *[]}} item
 * @param {ChildRuns[]} childrenRunsCollection
 * @param {boolean} [isChildRun=false]
 * @returns {*}
 */
function prepareRun (item, childrenRunsCollection, isChildRun = false) {
  const {
    id,
    pipelineRunParameters = [],
    childRunsCount = 0
  } = item;
  let parentRunId;
  let children;
  const parentId = pipelineRunParameters.find(p => p.name === 'parent-id');
  if (parentId && !Number.isNaN(Number(parentId.value)) && Number(parentId.value) !== 0) {
    parentRunId = Number(parentId.value);
  }
  if (!isChildRun && childRunsCount > 0) {
    const collection = childrenRunsCollection
      .find((childRuns) => childRuns.parentId === Number(id));
    if (!collection) {
      children = [{
        id: `${id}-children-placeholder`,
        [RUN_LOADING_PLACEHOLDER_PROPERTY]: true,
        [RUN_LOADING_ERROR_PROPERTY]: undefined
      }];
    } else if (collection.loaded) {
      const childRuns = (collection.childRuns || [])
        .map((child) => prepareRun(child, [], true));
      if (childRuns.length > 0) {
        children = childRuns;
      } else {
        children = [{
          id: `${id}-children-placeholder`,
          [RUN_LOADING_PLACEHOLDER_PROPERTY]: false,
          [RUN_LOADING_ERROR_PROPERTY]: 'Worker jobs not found'
        }];
      }
    } else {
      children = [{
        id: `${id}-children-placeholder`,
        [RUN_LOADING_PLACEHOLDER_PROPERTY]: collection.pending,
        [RUN_LOADING_ERROR_PROPERTY]: collection.error
      }];
    }
  }
  return {
    ...item,
    parentRunId,
    isChildRun,
    children
  };
}

function runIsService (run) {
  return run &&
    run.serviceUrl &&
    (
      run.status === 'RUNNING' ||
      run.status === 'PAUSED' ||
      run.status === 'PAUSING' ||
      run.status === 'RESUMING'
    ) &&
    run.initialized;
}

@inject('localization', 'routing', 'preferences')
@runPipelineActions
@observer
class RunTable extends localization.LocalizedReactComponent {
  static DefaultColumns = AllColumns;

  state = {
    page: 0,
    pending: false,
    error: undefined,
    total: 0,
    runs: [],
    filters: {},
    filtersState: {},
    expandedRows: []
  }

  fetchToken = 0;

  stopFetch;

  reFetch;
  setContinuous;

  /**
   * @type {ChildRuns[]}
   */
  @observable childrenRunsCollection = [];

  get columns () {
    const {
      columns = [],
      hiddenColumns = []
    } = this.props;
    const correctedColumns = columns && columns.length > 0 ? columns : AllColumns;
    return correctedColumns.filter((column) => !hiddenColumns.includes(column));
  }

  @computed
  get rolesFilterPreference () {
    const {preferences} = this.props;
    if (preferences.loaded) {
      return {...(preferences.uiRunsOwnersFilter || {})};
    }
    return {};
  }

  componentDidMount () {
    const {preferences} = this.props;
    preferences.fetchIfNeededOrWait();
    this.updateFromProps();
  }

  componentDidUpdate (prevProps) {
    const {
      filters: prevPropsFilters,
      searchFilters: prevPropsSearchFilters,
      search: prevPropsSearch,
      autoUpdate: prevPropsAutoUpdate
    } = prevProps;
    const {
      filters,
      searchFilters,
      search,
      autoUpdate
    } = this.props;
    if (
      search !== prevPropsSearch ||
      searchFilters !== prevPropsSearchFilters ||
      !filtersAreEqual(prevPropsFilters, filters) ||
      !autoUpdateConfigurationsEqual(prevPropsAutoUpdate, autoUpdate, false)
    ) {
      this.updateFromProps();
    } else if (
      !autoUpdateConfigurationsEqual(prevPropsAutoUpdate, autoUpdate, true)
    ) {
      this.reload();
    }
  }

  componentWillUnmount () {
    this.finishFetching();
    this.childrenRunsCollection = undefined;
  }

  updateFromProps = () => {
    this.setState({
      page: 0,
      error: undefined,
      expandedRows: [],
      filters: {},
      filtersState: {},
      runs: [],
      total: 0
    }, this.fetchCurrentPage.bind(this));
  };

  finishFetching = () => {
    if (typeof this.stopFetch === 'function') {
      this.stopFetch();
    }
    this.stopFetch = undefined;
    this.reFetch = undefined;
    this.setContinuous = undefined;
  }

  fetchCurrentPage = () => {
    this.setState({
      pending: true
    }, async () => {
      this.finishFetching();
      const {
        page,
        filters: userFilters = {}
      } = this.state;
      this.fetchToken += 1;
      let token = this.fetchToken;
      const {
        autoUpdate: autoUpdateProps,
        filters = {},
        pageSize = DEFAULT_PAGE_SIZE,
        search,
        searchFilters
      } = this.props;
      const {
        enabled: autoUpdate,
        intervalSeconds: interval = DEFAULT_INTERVAL_SECONDS
      } = parseAutoUpdateProps(autoUpdateProps);
      let fetchRunsPromise = () => Promise.resolve();
      const state = {
        pending: false,
        total: 0,
        runs: [],
        error: undefined
      };
      if (search) {
        if (searchFilters) {
          fetchRunsPromise = () => new Promise((resolve, reject) => {
            const request = new PipelineRunSearch();
            request
              .send({
                filterExpression: searchFilters || {},
                page: page + 1,
                pageSize: pageSize,
                timezoneOffsetInMinutes: -(new Date()).getTimezoneOffset()
              })
              .then(() => {
                if (request.networkError) {
                  state.error = request.networkError;
                } else if (request.loaded) {
                  state.runs = request.value.elements || [];
                  state.total = request.value.totalCount || 0;
                } else {
                  state.error = request.error || 'Error searching runs';
                }
                if (state.error) {
                  reject(new Error(state.error));
                } else {
                  resolve();
                }
              })
              .catch(reject);
          });
        }
      } else if (!search) {
        fetchRunsPromise = () => new Promise((resolve, reject) => {
          const modified = !filtersAreEqual(userFilters, {});
          const payload = getFiltersPayload({
            ...filters,
            ...userFilters,
            page: page + 1,
            pageSize
          });
          payload.userModified = payload.userModified || modified;
          if (
            !payload.statuses ||
            payload.statuses.length === 0
          ) {
            payload.statuses = AllStatuses;
          }
          const request = new PipelineRunSingleFilter(
            payload,
            false
          );
          request
            .filter(payload)
            .then(() => {
              if (request.networkError) {
                state.error = request.networkError;
              } else if (request.loaded) {
                state.runs = request.value || [];
                state.total = request.total || 0;
              } else {
                state.error = request.error || 'Error searching runs';
              }
              if (state.error) {
                reject(new Error(state.error));
              } else {
                resolve();
              }
            })
            .catch(reject);
        });
      }
      const call = async () => {
        await fetchRunsPromise();
        this.manageChildrenRunsCollection();
        // no awaiting
        this.fetchChildrenRunCollections();
      };
      const before = () => {
        token = this.fetchToken = (this.fetchToken || 0) + 1;
      };
      const after = () => {
        if (this.fetchToken === token) {
          this.setState(state);
          if (typeof autoUpdate === 'function') {
            const autoUpdateValue = autoUpdate(state);
            if (typeof this.setContinuous === 'function') {
              this.setContinuous(autoUpdateValue);
            }
          }
        }
      };
      const {
        stop,
        fetch: reFetch,
        setContinuous
      } = continuousFetch({
        continuous: !!autoUpdate,
        intervalMS: interval * 1000,
        afterInvoke: after,
        beforeInvoke: before,
        call
      });
      this.stopFetch = stop;
      this.reFetch = reFetch;
      this.setContinuous = setContinuous;
    });
  };

  filtersChanged = (newFilters, callback) => {
    const {
      filters: currentFilters
    } = this.state;
    this.setState(newFilters, () => {
      const {
        filters
      } = this.state;
      if (
        !filtersAreEqual(currentFilters, filters)
      ) {
        this.onPageChanged(0, true);
      }
      if (typeof callback === 'function') {
        callback();
      }
    });
  };

  reload = () => {
    if (typeof this.reFetch === 'function') {
      this.reFetch();
    }
  };

  onRunClick = (run) => {
    if (!run) {
      return;
    }
    const {
      routing
    } = this.props;
    if (routing) {
      routing.push(`/run/${run.id}`);
    }
  };

  onPageChanged = (newPage, force = false) => {
    const corrected = Math.max(0, newPage - 1);
    if (this.state.page !== corrected || force) {
      this.setState({page: corrected, expandedRows: []}, () => this.fetchCurrentPage());
    }
  };

  /**
   * @returns {ChildRuns[]}
   */
  manageChildrenRunsCollection = () => {
    const {
      expandedRows = [],
      runs
    } = this.state;
    const runIds = new Set(runs.map((run) => Number(run.id)));
    const corrected = expandedRows.filter((rowId) => runIds.has(rowId));
    const requestsToUpdate = [];
    this.childrenRunsCollection
      .forEach((collection) => {
        const disabled = !corrected.includes(collection.parentId);
        if (collection.disabled && !disabled) {
          requestsToUpdate.push(collection);
        }
        collection.disabled = disabled;
      });
    const newRequestIds = corrected
      .filter((id) => !this.childrenRunsCollection.some(
        (collection) => collection.parentId === Number(id))
      );
    const newRequests = newRequestIds.map((id) => new ChildRuns(Number(id)));
    this.childrenRunsCollection.push(
      ...newRequests
    );
    return [...requestsToUpdate, ...newRequests];
  };

  fetchChildrenRunCollection = async (collection) => {
    const {
      runs = []
    } = this.state;
    const run = runs.find((aRun) => aRun.id === collection.parentId);
    const {
      childRunsCount = 0
    } = run || {};
    await collection.fetch(childRunsCount);
  }

  fetchChildrenRunCollections = () => Promise.all(
    this.childrenRunsCollection
      .filter((collection) => !collection.disabled)
      .map(this.fetchChildrenRunCollection)
  );

  onExpandedRowsChanged = (expandedRows) => {
    const {
      expandedRows: currentExpandedRows = []
    } = this.state;
    if (!simpleArraysAreEqual(currentExpandedRows, expandedRows)) {
      this.setState(
        {expandedRows},
        async () => {
          const newRequests = this.manageChildrenRunsCollection();
          await Promise.all(newRequests.map(this.fetchChildrenRunCollection));
        }
      );
    }
  }

  renderCustomTable = (runs) => {
    const {
      tableClassName,
      customRunRenderer,
      onRunClick,
      runRowClassName = (aRun) => undefined,
      emptyDataMessage = 'No data'
    } = this.props;
    if (typeof customRunRenderer !== 'function') {
      return null;
    }
    const {
      pending
    } = this.state;
    return (
      <div
        className={
          classNames(
            'runs-table',
            tableClassName,
            styles.customTable,
            {
              [styles.pending]: pending
            }
          )
        }
      >
        {
          runs.length === 0 && !pending && (
            <Alert message={emptyDataMessage} type="info" />
          )
        }
        {
          runs.map((run) => (
            <div
              key={run.id}
              className={
                classNames(
                  `run-${run.id}`,
                  styles.run,
                  styles.customTableRow,
                  runRowClassName(run)
                )
              }
              onClick={() => (onRunClick || this.onRunClick)(run)}
            >
              {
                customRunRenderer(run)
              }
            </div>
          ))
        }
        {
          pending && (
            <div
              className={styles.loadingIndicator}
            >
              <LoadingView />
            </div>
          )
        }
      </div>
    );
  };

  renderTable = (runs) => {
    const {
      disableFilters,
      tableClassName,
      onRunClick,
      runRowClassName = (aRun) => undefined,
      emptyDataMessage = 'No data'
    } = this.props;
    const columns = getColumns(
      this.columns,
      {
        localizedString: this.localizedString.bind(this),
        reload: this.reload.bind(this),
        state: this.state,
        setState: this.filtersChanged.bind(this),
        disabledFilters: parseDisableFiltersProps(disableFilters),
        rolesFilterPreference: this.rolesFilterPreference
      }
    );
    const {
      pending,
      expandedRows
    } = this.state;
    return (
      <Table
        className={
          classNames(
            styles.table,
            'runs-table',
            tableClassName
          )
        }
        columns={[ExpandColumn, ...columns]}
        rowKey={record => record.id}
        rowClassName={(record) => classNames(
          `run-${record.id}`,
          runRowClassName(record),
          styles.run,
          {
            'cp-runs-table-service-url-run': runIsService(record)
          }
        )}
        dataSource={runs}
        onRowClick={onRunClick || this.onRunClick}
        pagination={false}
        loading={pending}
        size="small"
        indentSize={10}
        locale={{emptyText: emptyDataMessage, filterConfirm: 'OK', filterReset: 'Clear'}}
        expandedRowKeys={expandedRows}
        onExpandedRowsChange={this.onExpandedRowsChanged}
      />
    );
  };

  render () {
    const {
      className,
      pageSize = DEFAULT_PAGE_SIZE,
      paginationClassName,
      beforeTable,
      customRunRenderer
    } = this.props;
    const {
      runs: rawRuns = [],
      page,
      total,
      pending,
      error
    } = this.state;
    const runs = rawRuns.map((run) => prepareRun(run, this.childrenRunsCollection, false));
    let before = beforeTable;
    if (typeof beforeTable === 'function') {
      before = beforeTable({
        runs,
        total,
        error,
        pending
      });
    }
    return (
      <div className={className}>
        {before}
        {
          error && (
            <Alert
              message={error}
              type="error"
              style={{
                margin: '5px 0'
              }}
            />
          )
        }
        {
          typeof customRunRenderer === 'function'
            ? this.renderCustomTable(runs)
            : this.renderTable(runs)
        }
        {
          total > 0 && total > pageSize && (
            <div
              className={
                classNames(
                  paginationClassName,
                  styles.pagination
                )
              }
            >
              <Pagination
                total={total}
                pageSize={pageSize}
                current={page + 1}
                onChange={(newPage) => this.onPageChanged(newPage)}
                size="small"
              />
            </div>
          )
        }
      </div>
    );
  }
}

const RunTableColumnPropType = PropTypes.oneOf(AllColumns);

const StatusPropType = PropTypes.oneOf(AllStatuses);

RunTable.propTypes = {
  className: PropTypes.string,
  tableClassName: PropTypes.string,
  paginationClassName: PropTypes.string,
  customRunRenderer: PropTypes.func,
  emptyDataMessage: PropTypes.string,
  style: PropTypes.object,
  disableFilters: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.arrayOf(RunTableColumnPropType)
  ]),
  columns: PropTypes.arrayOf(RunTableColumnPropType),
  hiddenColumns: PropTypes.arrayOf(RunTableColumnPropType),
  filters: PropTypes.shape({
    statuses: PropTypes.arrayOf(StatusPropType),
    parentId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    pipelineIds: PropTypes.arrayOf(PropTypes.oneOfType([PropTypes.number, PropTypes.string])),
    versions: PropTypes.arrayOf(PropTypes.string),
    projectIds: PropTypes.arrayOf(PropTypes.oneOfType([PropTypes.number, PropTypes.string])),
    dockerImages: PropTypes.arrayOf(PropTypes.string),
    startDateFrom: PropTypes.oneOfType([PropTypes.string, PropTypes.object]),
    endDateTo: PropTypes.oneOfType([PropTypes.string, PropTypes.object]),
    owners: PropTypes.shape({
      owners: PropTypes.arrayOf(PropTypes.string),
      roles: PropTypes.arrayOf(PropTypes.string)
    }),
    onlyMasterJobs: PropTypes.bool,
    tags: PropTypes.object
  }),
  search: PropTypes.bool,
  searchFilters: PropTypes.object,
  pageSize: PropTypes.number,
  autoUpdate: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.func,
    PropTypes.shape({
      enabled: PropTypes.oneOfType([PropTypes.bool, PropTypes.func]),
      intervalSeconds: PropTypes.number,
      updateToken: PropTypes.any
    })
  ]),
  beforeTable: PropTypes.oneOfType([PropTypes.node, PropTypes.func]),
  onRunClick: PropTypes.func,
  runRowClassName: PropTypes.func
};

RunTable.defaultProps = {
  columns: RunTable.DefaultColumns,
  pageSize: DEFAULT_PAGE_SIZE
};

export {Columns};

export default RunTable;
