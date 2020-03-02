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
import {observable} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import FilterStore from './filter-store';
import PeriodFilter from './period-filter';
import ReportFilter from './report-filter';
import RunnerFilter, {RunnerType} from './runner-filter';
import reportsRouting from './reports-routing';
import Divider from './divider';
import ExportReports from '../export';
import styles from '../reports.css';

class Filters extends React.Component {
  static attach = (...opts) => inject('filters')(...opts);
  static reportsRoutes = reportsRouting;
  static runnerTypes = RunnerType;

  @observable filterStore = new FilterStore();

  componentWillReceiveProps (nextProps, nextContext) {
    this.filterStore.rebuild(this.props);
  }

  componentDidMount () {
    this.filterStore.rebuild(this.props);
  }

  render () {
    if (!this.filterStore) {
      return null;
    }
    const {children, users} = this.props;
    return (
      <div className={styles.container}>
        <div className={styles.reportFilter}>
          <ReportFilter
            filter={this.filterStore.report}
            onChange={this.filterStore.buildNavigationFn('report')}
          />
        </div>
        <div className={styles.billingContainer}>
          <div className={styles.periodFilters}>
            <PeriodFilter
              filter={this.filterStore.period}
              range={this.filterStore.range}
              onChange={this.filterStore.periodNavigation}
            />
            <Divider />
            <RunnerFilter
              filter={this.filterStore.runner}
              onChange={this.filterStore.buildNavigationFn('runner')}
            />
            <ExportReports
              className={styles.exportReportsButton}
              documentName={() => this.filterStore.getDescription({users})}
            />
          </div>
          <Provider filters={this.filterStore}>
            <ExportReports.Provider>
              {children}
            </ExportReports.Provider>
          </Provider>
        </div>
      </div>
    );
  }
}

export default inject('users')(observer(Filters));
