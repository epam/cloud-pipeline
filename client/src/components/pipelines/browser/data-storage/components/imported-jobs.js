/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observable} from 'mobx';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import moment from 'moment-timezone';
import {
  message,
  DatePicker,
  Select,
  Button,
  Icon
} from 'antd';
import LoadingView from '../../../../special/LoadingView';
import OmicsJobsImport from '../../../../../models/dataStorage/OmicsJobsImport';
import displayDate from '../../../../../utils/displayDate';
import styles from './imported-jobs.css';

const PAGE_SIZE = 10;
const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss.SSS';
const STATUSES = [
  'SUBMITTED',
  'IN_PROGRESS',
  'CANCELLING',
  'CANCELLED',
  'FAILED',
  'COMPLETED',
  'COMPLETED_WITH_FAILURES'
];

const momentDateParser = (d) => {
  return d
    ? moment(moment.utc(d, DATE_FORMAT).toDate())
    : undefined;
};
const momentDateConverter = (d) => {
  return d
    ? moment.utc(d).format(DATE_FORMAT)
    : undefined;
};
const getDisabledDate = ({min, max}) => (date) => {
  let disabled = false;
  if (min) {
    disabled = disabled || date < min;
  }
  if (max) {
    disabled = disabled || date > max;
  }
  return disabled;
};

function Filter ({addonBefore, label, children, display = true}) {
  if (!display) {
    return null;
  }
  return (
    <div className={classNames(styles.jobFilter)}>
      {addonBefore}
      {label && (<span className={styles.jobLabel}>{label}:</span>)}
      {children}
    </div>
  );
}

export default class JobList extends React.Component {
  @observable list = [];

  static propTypes = {
    storageId: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ])
  };

  state = {
    pending: false,
    pageIndex: 0,
    pages: [undefined],
    emptyPlaceholder: undefined
  };

  get pageSize () {
    return PAGE_SIZE;
  }

  get statuses () {
    return STATUSES;
  }

  get currentPage () {
    const {pageIndex, pages} = this.state;
    if (pageIndex >= 0 && pageIndex < pages.length) {
      return pages[pageIndex];
    }
    return undefined;
  }

  get canNavigateToPreviousPage () {
    const {pageIndex} = this.state;
    return pageIndex > 0;
  }

  get canNavigateToNextPage () {
    const {pageIndex, pages} = this.state;
    return pages.length > pageIndex + 1;
  }

  setDefaultFilter = () => {
    this.onFiltersChange({
      timestampFrom: moment.utc().add(-1, 'd').format(DATE_FORMAT),
      status: 'IN_PROGRESS'
    });
  };

  onFiltersChange = (newFilters) => {
    this.setState({
      filters: {...newFilters}
    });
  };

  getPayload = () => {
    const payload = {};
    if (this.state.filters) {
      const {timestampFrom, timestampTo, status} = this.state.filters;
      payload.createdAfter = timestampFrom;
      payload.createdBefore = timestampTo;
      payload.status = status;
    }
    return payload;
  }

  getJobList = async () => {
    if (!this.props.storageId) {
      return;
    }
    return new Promise(resolve => {
      const hide = message.loading('Getting AWS HealthOmics Import jobs...');
      const request = new OmicsJobsImport(this.props.storageId, this.pageSize, this.currentPage);
      const payload = this.getPayload();
      request.send(payload)
        .then(() => {
          if (request.loaded) {
            hide();
            return resolve(request.value);
          } else {
            hide();
            throw new Error(request.error || 'Error getting AWS HealthOmics Import jobs');
          }
        })
        .catch(e => {
          hide();
          message.error(e.message, 5);
          return resolve();
        });
    });
  }

  setList = (result) => {
    if (result && result.jobs && result.jobs.length) {
      this.list = result.jobs.map(job => ({
        id: job.id,
        date: displayDate(job.creationTime),
        status: job.status
      }));
    } else {
      this.list = [];
    }
    this.setEmptyPlaceHolder();
  }

  setToken = (jobs) => {
    const {nextToken} = jobs || {};
    if (nextToken) {
      const {pages} = this.state;
      pages.push(nextToken);
      this.setState({pages});
    }
  }

  setJobList = () => {
    this.setState({
      pending: true
    }, async () => {
      const jobs = await this.getJobList();
      this.setToken(jobs);
      this.setList(jobs);
      this.setState({pending: false});
    });
  }

  navigateToPreviousPage = () => {
    const {pageIndex, pages} = this.state;
    this.setState({
      pages: pages.slice(0, pageIndex),
      pageIndex: pageIndex - 1
    }, this.setJobList);
  };

  navigateToNextPage = () => {
    const {pageIndex} = this.state;
    this.setState({
      pageIndex: pageIndex + 1
    }, this.setJobList);
  };

  renderHeader = () => {
    const {filters = {}} = this.state;
    const {
      timestampFrom,
      timestampTo,
      status
    } = filters;

    const onFieldChanged = (
      field,
      converter = (o => o),
      eventField = 'value'
    ) => (event) => {
      let value = event;
      if (event && event.target) {
        value = event.target[eventField];
      }
      const newFilters = Object.assign({}, filters, {[field]: converter(value)});
      if (!value) {
        delete newFilters[field];
      }
      this.onFiltersChange(newFilters);
    };

    const commonStyle = {flex: '1 1 200px', minWidth: '154px', maxWidth: '400px'};

    return (
      <div className={styles.jobFilters}>
        <Filter label="From">
          <DatePicker
            showTime
            size="small"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="From"
            style={commonStyle}
            value={momentDateParser(timestampFrom)}
            onChange={onFieldChanged('timestampFrom', momentDateConverter)}
            disabledDate={getDisabledDate({max: momentDateParser(timestampTo)})}
          />
        </Filter>
        <Filter label="To">
          <DatePicker
            showTime
            size="small"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="To"
            style={commonStyle}
            value={momentDateParser(timestampTo)}
            onChange={onFieldChanged('timestampTo', momentDateConverter)}
            disabledDate={getDisabledDate({min: momentDateParser(timestampFrom)})}
          />
        </Filter>
        <Filter label="Status">
          <Select
            size="small"
            allowClear
            showSearch
            dropdownMatchSelectWidth={false}
            optionLabelProp="label"
            placeholder="Status"
            style={commonStyle}
            filterOption={
              (input, option) =>
                (option.props.value || '').toLowerCase().includes(input.toLowerCase()) ||
                (option.props.attributesString || '').toLowerCase().includes(input.toLowerCase())
            }
            value={status}
            onChange={onFieldChanged('status')}
          >
            {
              this.statuses.map(s => (
                <Select.Option
                  key={s}
                  value={s}
                  label={s}
                >
                  {s}
                </Select.Option>
              ))
            }
          </Select>
        </Filter>
        <div className={classNames(styles.jobBtnContainer)}>
          <Button
            onClick={this.setJobList}
            disabled={this.state.pending}
            className={styles.searchButton}
            size="small"
          >
            SEARCH
          </Button>
        </div>
      </div>);
  }

  renderList = () => {
    if (!this.list || !this.list.length) return null;
    return (
      <div>
        {this.list && this.list.map(j => (
          <div key={j.id} className={classNames('cp-divider', 'bottom', styles.jobContainer)}>
            <div className={styles.jobDiv}>
              <span className={styles.jobId}>{j.id}</span>
              <span>{j.date}</span>
            </div>
            <div className={styles.jobDiv}>{j.status}</div>
          </div>
        ))}
      </div>
    );
  }

  setEmptyPlaceHolder = () => {
    const filters = Object.entries(this.state.filters || {});
    const {status, timestampFrom} = this.state.filters || {};
    const statusExists = status !== undefined;
    let message;
    if (!filters.length) {
      message = 'No jobs found';
    } else if (filters.length > 1) {
      message = `No jobs found 
        ${statusExists ? 'with' : 'in'} 
        the specified 
        ${statusExists ? 'filters' : 'time period'}`;
    } else {
      message = `No jobs found 
        ${statusExists ? 'with' : (timestampFrom ? 'after' : 'before')} 
        the specified 
        ${statusExists ? 'status' : 'time'}`;
    }
    this.setState({emptyPlaceholder: message});
  }

  renderEmptyPlaceholder = () => {
    if (this.list && this.list.length) return null;
    return <div className={styles.emptyPlaceholder}>{this.state.emptyPlaceholder}</div>;
  }

  renderPagination = () => {
    if (!this.list || !this.list.length) return null;
    return (
      <div className={styles.pagination}>
        <Button
          className={styles.button}
          size="small"
          disabled={!this.canNavigateToPreviousPage}
          onClick={this.navigateToPreviousPage}
        >
          <Icon type="caret-left" />
        </Button>
        <Button
          className={styles.button}
          size="small"
          disabled={!this.canNavigateToNextPage}
          onClick={this.navigateToNextPage}
        >
          <Icon type="caret-right" />
        </Button>
      </div>
    );
  }

  render () {
    if (this.state.pending) {
      return <LoadingView key="job-pending" />;
    }
    return (
      <div>
        {this.renderHeader()}
        {this.renderList()}
        {this.renderEmptyPlaceholder()}
        {this.renderPagination()}
      </div>
    );
  };

  clearPagination = () => {
    this.setState({
      pageIndex: 0,
      pages: [undefined]
    });
  };

  componentDidMount () {
    this.setDefaultFilter();
    this.setJobList();
  };

  componentDidUpdate (prevProps, prevState) {
    if (!this.state.filters) {
      this.clearPagination();
      this.setDefaultFilter();
      this.setJobList();
    } else {
      if (prevProps.storageId !== this.props.storageId) {
        this.clearPagination();
        this.setJobList();
      }
    }
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.updateJobsSearch) {
      this.setJobList();
    }
  }

  componentWillUnmount () {
    this.setState({pending: false});
  }
}
