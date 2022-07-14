/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import {Period as ReportPeriod} from '../../../special/periods';

const periods = {
  month: 'MONTH',
  quarter: 'QUARTER',
  year: 'YEAR'
};

const periodNames = {
  [periods.month]: 'Month',
  [periods.quarter]: 'Quarter',
  [periods.year]: 'Year'
};

const periodNamesAdjective = {
  [periods.month]: 'Monthly',
  [periods.quarter]: 'Quarterly',
  [periods.year]: 'Annual'
};

export function getQuotaPeriodForReportPeriod (reportPeriod) {
  switch (reportPeriod) {
    case ReportPeriod.quarter:
      return periods.quarter;
    case ReportPeriod.year:
      return periods.year;
    default:
      return periods.month;
  }
}

export {periodNames, periodNamesAdjective};
export default periods;
