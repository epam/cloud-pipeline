/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import moment from 'moment-timezone';

export default function evaluateRunDuration (run) {
  if (run.runStatuses) {
    const startDate = moment.utc(run.startDate);
    const endDate = run.endDate ? moment.utc(run.endDate) : moment.utc();
    const dates = run.runStatuses
      .map(r => ({status: r.status, timestamp: moment.utc(r.timestamp)}));
    dates.push({
      status: 'RUNNING',
      timestamp: startDate
    });
    dates.push({
      status: 'STOPPED',
      timestamp: endDate
    });
    dates.sort((dA, dB) => {
      if (dA.timestamp > dB.timestamp) {
        return 1;
      } else if (dA.timestamp < dB.timestamp) {
        return -1;
      }
      return 0;
    });
    let activeDuration = 0;
    for (let i = 0; i < dates.length - 1; i++) {
      const start = dates[i];
      const end = dates[i + 1];
      if (/^RUNNING$/i.test(start.status)) {
        activeDuration += (end.timestamp.diff(start.timestamp, 'hours', true));
      }
    }
    return activeDuration;
  } else {
    const endDate = run.endDate ? moment.utc(run.endDate) : moment.utc();
    return endDate.diff(moment.utc(run.startDate), 'hours', true);
  }
}
