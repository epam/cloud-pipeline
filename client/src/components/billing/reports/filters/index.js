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
import {observer} from 'mobx-react';
import Discounts from '../discounts';
import PeriodFilter from '../../../special/reports/filters/period-filter';
import RunnerFilter from '../../../special/reports/filters/runner-filter';
import ProviderFilter from './provider-filter';
import Divider from '../../../special/reports/filters/divider';
import {RestoreButton} from '../layout';
import ExportReports from '../export';
import ReportNavigation from '../../../special/reports/navigation';
import roleModel from '../../../../utils/roleModel';
import styles from '../../../special/reports/reports.css';
import reportTypes from '../../../special/reports/navigation/report-types';
function Filters ({children}) {
  return (
    <div className={styles.container}>
      <div className={styles.filtersContainer}>
        <div className={styles.filters}>
          <PeriodFilter type={reportTypes.billing} />
          <Divider />
          {
            roleModel.manager.billing(
              <RunnerFilter />,
              'runner filter'
            )
          }
          <Divider />
          <ProviderFilter />
        </div>
        <div className={styles.actionsBlock}>
          {
            roleModel.manager.billing(
              <Discounts.Button className={styles.discountsButton} />,
              'discounts button'
            )
          }
          <RestoreButton className={styles.restoreLayoutButton} />
          <ExportReports className={styles.exportReportsButton} />
        </div>
      </div>
      <div className={styles.dataContainer}>
        <ExportReports.Provider>
          {children}
        </ExportReports.Provider>
      </div>
    </div>
  );
}

export default ReportNavigation.attach(observer(Filters));
