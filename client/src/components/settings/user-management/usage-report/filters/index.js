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

import PeriodFilter from './period-filter';
import Divider from '../../../../special/reports/divider';
import {Period} from '../../../../special/periods';
import styles from '../reports.css';
import UsageNavigation from '../navigation';
import UserFilter from './user-filter';

const periods = [Period.month, Period.day];
function Filters ({children}) {
  return (
    <div className={styles.container}>
      <div className={styles.filtersContainer}>
        <div className={styles.filters}>
          <PeriodFilter periods={periods} />
          <Divider />
          <UserFilter />
        </div>
      </div>
      <div className={styles.dataContainer}>
        {children}
      </div>
    </div>
  );
}

export default UsageNavigation.attach(observer(Filters));
