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

import moment from 'moment-timezone';

export default function getPeriodMonths (periodInfo) {
  if (!periodInfo) {
    return null;
  }
  const {start, endStrict} = periodInfo;
  if (endStrict.diff(start, 'M') > 0) {
    let d = moment(start);
    const periods = [];
    while (d < endStrict) {
      const start = moment(d);
      let end = moment(d).endOf('M');
      if (end > endStrict) {
        end = endStrict;
      }
      periods.push({start, end, endStrict: end});
      d = d.add(1, 'M');
    }
    return periods;
  }
  return null;
}
