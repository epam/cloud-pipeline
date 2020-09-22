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
import Discounts from '../discounts';
import FilterStore from './filter-store';
import PeriodFilter from './period-filter';
import ReportFilter from './report-filter';
import RunnerFilter, {RunnerType} from './runner-filter';
import ProviderFilter from './provider-filter';
import reportsRouting from './reports-routing';
import Divider from './divider';
import {RestoreButton} from '../layout';
import ExportReports, {ExportFormat} from '../export';
import roleModel from '../../../../utils/roleModel';
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

  componentDidUpdate (prevProps) {
    const {location} = this.props;
    if (location) {
      const {pathname, search} = location;
      const {pathname: prevPathname, search: prevSearch} = prevProps.location;
      if (prevSearch !== search || prevPathname !== pathname) {
        this.filterStore.rebuild(this.props);
      }
    }
  }

  render () {
    if (!this.filterStore) {
      return null;
    }
    const {children, users, cloudRegionsInfo} = this.props;
    const exportFormats = /^general$/i.test(this.filterStore.report)
      ? [ExportFormat.csvCostCenters, ExportFormat.csvUsers, ExportFormat.image]
      : [ExportFormat.csv, ExportFormat.image];
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
            {
              roleModel.manager.billing(
                <RunnerFilter
                  filter={this.filterStore.runner}
                  onChange={this.filterStore.buildNavigationFn('runner')}
                />,
                'runner filter'
              )
            }
            <Divider />
            <ProviderFilter
              filter={this.filterStore.region}
              onChange={this.filterStore.buildNavigationFn('region')}
            />
            <div className={styles.actionsBlock}>
              {
                roleModel.manager.billing(
                  <Discounts.Button className={styles.discountsButton} />,
                  'discounts button'
                )
              }
              <RestoreButton className={styles.restoreLayoutButton} />
              <ExportReports
                className={styles.exportReportsButton}
                documentName={() => this.filterStore.getDescription({users, cloudRegionsInfo})}
                formats={exportFormats}
              />
            </div>
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

const RUNNER_SEPARATOR = FilterStore.RUNNER_SEPARATOR;
const REGION_SEPARATOR = FilterStore.REGION_SEPARATOR;

export {RUNNER_SEPARATOR, REGION_SEPARATOR};
export default inject('users', 'cloudRegionsInfo')(roleModel.authenticationInfo(observer(Filters)));
