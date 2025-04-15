/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import {Input, Select, DatePicker} from 'antd';
import {checkDateInRange} from '../utils';
import moment from 'moment-timezone';

export default class Filters extends React.Component {
  get filtersApplied () {
    const {filters = {}} = this.props;
    const {from, to, ...rest} = filters;
    return Object.values(rest).some(filter => {
      if (Array.isArray(filter)) {
        return filter.length > 0;
      }
      return filter !== undefined && filter !== '';
    });
  };

  onChangeStringFilter = (filterKey) => (event) => {
    const {onChange, filters = {}} = this.props;
    if (onChange) {
      onChange({
        ...filters,
        [filterKey]: event.target.value
      });
    }
  };

  onChangeSelectFilter = (filterKey) => (values) => {
    const {onChange, filters = {}} = this.props;
    if (onChange) {
      onChange({
        ...filters,
        [filterKey]: values
      });
    }
  };

  resetFilters = () => {
    const {onChange, defaultFilters = {}} = this.props;
    if (onChange) {
      onChange(defaultFilters);
    }
  };

  onDateChanged = (key) => (date, a, b) => {
    const {onChange, filters = {}} = this.props;
    if (onChange) {
      onChange({
        ...filters,
        [key]: moment(date)
      });
    }
  }

  disabledStartDate = (date) => {
    const {filters = {}} = this.props;
    const endValue = filters.to;
    return checkDateInRange(date, undefined, endValue);
  };

  disabledEndDate = (date) => {
    const {filters = {}} = this.props;
    const startValue = filters.from;
    return checkDateInRange(date, startValue);
  };

  render () {
    const {filters = {}, availableFilters = {}} = this.props;
    const methods = availableFilters.method || [];
    const reporters = availableFilters.reporter || [];
    return (
      <div style={{display: 'flex', gap: '5px', alignItems: 'center', position: 'relative'}}>
        <Input
          placeholder="Host name"
          onChange={this.onChangeStringFilter('hostname')}
          value={filters['hostname']}
        />
        <Input placeholder="Host IP" onChange={this.onChangeStringFilter('hostIp')} />
        <Input
          placeholder="Run ID"
          onChange={this.onChangeStringFilter('runId')}
          value={filters['runId']}
        />
        <Input
          placeholder="Resource host"
          onChange={this.onChangeStringFilter('resourceHost')}
          value={filters['resourceHost']}
        />
        {reporters.length > 1 ? (
          <Select
            allowClear
            mode="multiple"
            style={{minWidth: 200}}
            placeholder="Reporter"
            value={filters.reporter}
            onChange={this.onChangeSelectFilter('reporter')}
            getPopupContainer={triggerNode => triggerNode}
          >
            {reporters.map(reporter => (
              <Select.Option key={reporter} value={reporter}>
                {reporter}
              </Select.Option>
            ))}
          </Select>
        ) : null}
        {methods.length > 1 ? (
          <Select
            allowClear
            mode="multiple"
            style={{minWidth: 140}}
            placeholder="Method"
            value={filters.method}
            onChange={this.onChangeSelectFilter('method')}
            getPopupContainer={triggerNode => triggerNode}
          >
            {methods.map(method => (
              <Select.Option key={method} value={method}>
                {`${method[0]}${method.substring(1).toLowerCase()}`}
              </Select.Option>
            ))}
          </Select>
        ) : null}
        <DatePicker
          allowClear={false}
          format="YYYY-MM-DD HH:mm"
          placeholder="Start"
          onChange={this.onDateChanged('from')}
          style={{minWidth: 150}}
          value={filters.from}
          disabledDate={this.disabledStartDate}
        />
        <DatePicker
          allowClear={false}
          format="YYYY-MM-DD HH:mm"
          placeholder="End"
          onChange={this.onDateChanged('to')}
          style={{minWidth: 150}}
          value={filters.to}
          disabledDate={this.disabledEndDate}
        />
        {this.filtersApplied ? (
          <a onClick={this.resetFilters}>Reset</a>
        ) : null}
      </div>
    );
  }
}

Filters.propTypes = {
  onChange: PropTypes.func,
  availableFilters: PropTypes.shape({
    method: PropTypes.arrayOf(PropTypes.string),
    reporter: PropTypes.arrayOf(PropTypes.string)
  }),
  filters: PropTypes.shape({
    hostname: PropTypes.string,
    hostIp: PropTypes.string,
    runId: PropTypes.string,
    resourceHost: PropTypes.string,
    method: PropTypes.arrayOf(PropTypes.string),
    reporter: PropTypes.arrayOf(PropTypes.string),
    from: PropTypes.instanceOf(moment),
    to: PropTypes.instanceOf(moment)
  }),
  defaultFilters: PropTypes.shape({
    hostname: PropTypes.string,
    hostIp: PropTypes.string,
    runId: PropTypes.string,
    resourceHost: PropTypes.string,
    method: PropTypes.arrayOf(PropTypes.string),
    reporter: PropTypes.arrayOf(PropTypes.string),
    from: PropTypes.instanceOf(moment),
    to: PropTypes.instanceOf(moment)
  })
};
