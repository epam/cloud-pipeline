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

import {Statuses} from '../../special/run-status-icon';
import moment from 'moment';

export default function (run) {
  if (!run) {
    return null;
  }
  const {runStatuses, status} = run;
  run.resumeFailureReason = undefined;
  if (runStatuses && runStatuses.length > 0 && status === Statuses.paused) {
    const sortedStatuses = runStatuses
      .sort((a, b) => moment.utc(a.timestamp).diff(moment.utc(b.timestamp)));
    const lastStatus = sortedStatuses[sortedStatuses.length - 1];
    if (lastStatus && lastStatus.status === Statuses.paused && lastStatus.reason) {
      run.resumeFailureReason = lastStatus.reason;
    }
  }
  return run;
}
