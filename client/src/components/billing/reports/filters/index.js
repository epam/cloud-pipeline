/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, Provider} from 'mobx-react';
import PeriodFilter from './period-filter';
import ReportFilter from './report-filter';
import RunnerFilter, {RunnerType} from './runner-filter';
import Divider from './divider';
import {Period} from '../periods';
import ReportsRouting from '../routing';
import styles from '../reports.css';

class Filter {
  period;
  range;
  report;
  runner;

  rebuild = ({location, router}) => {
    this.router = router;
    const {
      period = Period.month,
      user,
      group,
      range
    } = (location || {}).query || {};
    if (user) {
      this.runner = {
        type: RunnerType.user,
        id: user
      };
    } else if (group) {
      this.runner = {
        type: RunnerType.group,
        id: group
      };
    } else {
      this.runner = undefined;
    }
    this.report = ReportsRouting.parse(location);
    this.period = period;
    this.range = range;
  };

  navigate = (navigation, strictRange = false) => {
    let {report, runner, period, range} = navigation || {};
    if (report === undefined) {
      report = this.report;
    }
    if (runner === undefined) {
      runner = this.runner;
    }
    if (period === undefined) {
      period = this.period;
    }
    if (range === undefined && !strictRange) {
      range = this.range;
    }
    const params = [
      runner && runner.type === RunnerType.user && `user=${runner.id}`,
      runner && runner.type === RunnerType.group && `group=${runner.id}`,
      period && `period=${period}`,
      range && `range=${range}`
    ].filter(Boolean);
    let query = '';
    if (params.length) {
      query = `?${params.join('&')}`;
    }
    if (this.router) {
      this.router.push(`${ReportsRouting.getPath(report)}${query}`);
    }
  };

  buildNavigationFn = (property) => e => this.navigate({[property]: e});

  periodNavigation = (period, range) => this.navigate({period, range}, true);
}

class Filters extends React.Component {
  reportsFilter = new Filter();

  componentWillReceiveProps (nextProps, nextContext) {
    this.reportsFilter.rebuild(this.props);
  }

  componentDidMount () {
    this.reportsFilter.rebuild(this.props);
  }

  render () {
    if (!this.reportsFilter) {
      return null;
    }
    const {children} = this.props;
    return (
      <div className={styles.container}>
        <div className={styles.reportFilter}>
          <ReportFilter
            filter={this.reportsFilter.report}
            onChange={this.reportsFilter.buildNavigationFn('report')}
          />
        </div>
        <div className={styles.billingContainer}>
          <div className={styles.periodFilters}>
            <PeriodFilter
              filter={this.reportsFilter.period}
              range={this.reportsFilter.range}
              onChange={this.reportsFilter.periodNavigation}
            />
            <Divider />
            <RunnerFilter
              filter={this.reportsFilter.runner}
              onChange={this.reportsFilter.buildNavigationFn('runner')}
            />
          </div>
          <Provider reportsFilter={this.reportsFilter}>
            {children}
          </Provider>
        </div>
      </div>
    );
  }
}

export {RunnerType, Filter};

export const injectFilters = (...opts) => inject('reportsFilter')(...opts);
export default Filters;
