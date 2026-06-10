/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import dayjs from '../../../../utils/dayjs';

export default function getPeriodMonths(periodInfo) {
  if (!periodInfo) {
    return null;
  }
  const {start, endStrict} = periodInfo;
  const startOfMonth = dayjs(start).startOf('month');
  const endOfMonth = dayjs(endStrict).endOf('month');
  if (endOfMonth.diff(startOfMonth, 'month') > 0) {
    let d = dayjs(start);
    const periods = [];
    while (d.valueOf() < endOfMonth.valueOf()) {
      const periodStart = dayjs(d);
      let end = dayjs(d).endOf('month');
      if (end.valueOf() > endStrict.valueOf()) {
        end = endStrict;
      }
      periods.push({start: periodStart, end, endStrict: end});
      d = d.add(1, 'month').startOf('month');
    }
    return periods;
  }
  return null;
}
