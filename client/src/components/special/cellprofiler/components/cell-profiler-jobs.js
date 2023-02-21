/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {Alert, Icon, Pagination} from 'antd';
import {filtersAreEqual, getBatchJobs} from '../model/analysis/batch';
import CellProfilerJob from './components/cell-profiler-job';
import CellProfilerJobsFilters from './components/cell-profiler-jobs-filters';
import CellProfilerExternalJobs from './external-jobs/external-jobs';
import styles from './cell-profiler.css';

const PAGE_SIZE = 2;
const REFRESH_TIMEOUT_MS = 1000 * 5;

class CellProfilerJobs extends React.Component {
  state = {
    pending: true,
    blocking: true,
    error: undefined,
    page: 0,
    pageSize: PAGE_SIZE,
    total: 0,
    jobs: [],
    selectedJobId: undefined,
    filters: undefined
  };

  get selectedJobId () {
    const {
      jobId,
      onJobSelected
    } = this.props;
    if (typeof onJobSelected === 'function') {
      // controlled
      return jobId;
    }
    const {
      selectedJobId
    } = this.state;
    return selectedJobId;
  }

  get filters () {
    const {
      filters: propsFilters,
      onFiltersChange
    } = this.props;
    if (typeof onFiltersChange === 'function') {
      // controlled
      return propsFilters;
    }
    const {
      filters
    } = this.state;
    return filters;
  }

  get paginationControlAvailable () {
    const {
      total,
      pageSize
    } = this.state;
    return total > pageSize;
  }

  componentDidMount () {
    this.refresh();
    this.checkSize();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!filtersAreEqual(prevProps.filters, this.props.filters, true)) {
      this.onResetFilters();
    }
  }

  componentWillUnmount () {
    clearTimeout(this.refreshTimeoutHandle);
    cancelAnimationFrame(this.containerSizeChecker);
    this.container = undefined;
  }

  refresh = () => {
    clearTimeout(this.refreshTimeoutHandle);
    const {
      openFirst
    } = this.props;
    const {
      page,
      pageSize
    } = this.state;
    this.setState({
      pending: true
    }, async () => {
      const state = {
        pending: false,
        error: undefined,
        blocking: false
      };
      try {
        const {
          jobs = [],
          total = 0,
          page: dataPage,
          pageSize: dataPageSize
        } = await getBatchJobs({
          page,
          pageSize,
          ...(this.filters || {})
        });
        const {
          page: currentPage,
          pageSize: currentPageSize
        } = this.state;
        if (currentPage !== dataPage || dataPageSize < currentPageSize) {
          return;
        }
        state.jobs = jobs;
        state.total = total;
      } catch (error) {
        state.error = error.message;
      } finally {
        this.setState(state, () => {
          const {
            jobs = []
          } = this.state;
          if (jobs.length && openFirst && !this.selectedJobId) {
            this.onSelectJob(jobs[0]);
          }
        });
        this.refreshTimeoutHandle = setTimeout(this.refresh, REFRESH_TIMEOUT_MS);
      }
    });
  };

  checkSize = () => {
    const {pending, error} = this.state;
    cancelAnimationFrame(this.containerSizeChecker);
    if (this.container) {
      const height = this.container.clientHeight;
      if (!pending && !error && (height !== this.componentHeight)) {
        this.componentHeight = height;
        const DEFAULT_HEIGHT_PX = 46;
        const newPageSize = Math.floor(height / DEFAULT_HEIGHT_PX);
        const {pageSize: currentPageSize} = this.state;
        if (currentPageSize !== newPageSize) {
          this.setState({
            pageSize: newPageSize
          }, () => {
            if (newPageSize > currentPageSize) {
              this.refresh();
            }
          });
        }
      }
    }
    this.containerSizeChecker = requestAnimationFrame(this.checkSize);
  };

  onChangePage = (page) => {
    if (this.state.page !== page - 1) {
      this.setState({
        page: page - 1,
        blocking: true
      }, this.refresh);
    }
  };

  onResetFilters = () => {
    this.setState({
      page: 0,
      blocking: true
    }, this.refresh);
  };

  onSelectJob = (job) => {
    const {
      onJobSelected
    } = this.props;
    if (typeof onJobSelected === 'function') {
      // controlled
      onJobSelected(job ? job.id : undefined);
      return;
    }
    this.setState({
      selectedJobId: job ? job.id : undefined
    });
  }

  onInitializeJobsContainer = (component) => {
    this.container = component;
  };

  onChangeFilters = (newFilters = {}) => {
    const {
      onFiltersChange
    } = this.props;
    if (typeof onFiltersChange === 'function') {
      onFiltersChange(newFilters);
    } else {
      this.setState({
        filters: newFilters
      }, this.onResetFilters);
    }
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const {
      error,
      pending,
      blocking,
      jobs = [],
      total = 0,
      page = 0,
      pageSize
    } = this.state;
    return (
      <div
        className={
          classNames(
            styles.cellProfilerJobsContainer,
            className,
            'cp-panel',
            'cp-panel-borderless'
          )
        }
        style={style}
      >
        <CellProfilerJobsFilters
          filters={this.filters}
          onChange={this.onChangeFilters}
        />
        <CellProfilerExternalJobs
          className={styles.cellProfilerOtherJobs}
          selected={this.selectedJobId}
          onSelect={this.onSelectJob}
          filters={this.filters}
          onChangeFilters={this.onChangeFilters}
        />
        {
          error && (
            <Alert
              className={styles.cellProfilerJobsRow}
              message={error}
              type="error"
            />
          )
        }
        {
          !pending && !total && (
            <Alert
              className={styles.cellProfilerJobsRow}
              message="Jobs not found"
              type="warning"
            />
          )
        }
        <div
          className={styles.cellProfilerJobs}
          ref={this.onInitializeJobsContainer}
        >
          {
            jobs.length === 0 && pending && (
              <div
                className={styles.cellProfilerJobsRow}
              >
                <Icon type="loading" />
                <span
                  className="cp-text-not-important"
                  style={{marginLeft: 5}}
                >
                  Fetching jobs...
                </span>
              </div>
            )
          }
          {
            jobs.slice(0, pageSize).map(aJob => (
              <CellProfilerJob
                key={aJob.id}
                job={aJob}
                className={
                  classNames(
                    'cell-profiler-job',
                    {
                      'selected': Number(aJob.id) === Number(this.selectedJobId)
                    }
                  )
                }
                onClick={() => this.onSelectJob(aJob)}
                pending={blocking}
              />
            ))
          }
        </div>
        {
          this.paginationControlAvailable && (
            <div
              className={styles.cellProfilerPagination}
            >
              <Pagination
                disabled={blocking}
                total={total}
                current={page + 1}
                pageSize={pageSize}
                onChange={this.onChangePage}
                size="small"
              />
            </div>
          )
        }
      </div>
    );
  }
}

CellProfilerJobs.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  jobId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  onJobSelected: PropTypes.func,
  filters: PropTypes.object,
  onFiltersChange: PropTypes.func,
  openFirst: PropTypes.bool
};

export default CellProfilerJobs;
