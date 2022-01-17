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

import PeriodFilter from '../../../../special/reports/filters/period-filter';
import RunnerFilter from '../../../../special/reports/filters/runner-filter';
import Divider from '../../../../special/reports/filters/divider';
import styles from '../../../../special/reports/reports.css';
import reportTypes from '../../../../special/reports/navigation/report-types';

function Filters ({children}) {
  return (
    <div className={styles.container}>
      <div className={styles.filtersContainer}>
        <div className={styles.filters}>
          <PeriodFilter type={reportTypes.usage} />
          <Divider />
          <RunnerFilter type={reportTypes.usage} />
        </div>
      </div>
      <div className={styles.dataContainer}>
        {children}
      </div>
    </div>
  );
}
export default Filters;
