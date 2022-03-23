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

import moment from 'moment-timezone';
import QuotaPeriods from '../../../quotas/utilities/quota-periods';
import {Period} from '../../../../special/periods';

export function getAffectiveQuotaPeriods (request) {
  if (!request || !request.filters) {
    return [];
  }
  const {start, name} = request.filters;
  const periods = [];
  const now = moment.utc();
  const compareByUnit = unit => moment(start).startOf(unit).isSame(moment(now).startOf(unit));
  if (compareByUnit('year')) {
    periods.push(QuotaPeriods.year);
  }
  if ([Period.custom, Period.month, Period.quarter].includes(name) && compareByUnit('quarter')) {
    periods.push(QuotaPeriods.quarter);
  }
  if ([Period.custom, Period.month].includes(name) && compareByUnit('month')) {
    periods.push(QuotaPeriods.month);
  }
  return periods;
}
