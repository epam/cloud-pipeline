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

const Statuses = {
  running: 'RUNNING',
  pausing: 'PAUSING',
  paused: 'PAUSED',
  resuming: 'RESUMING',
  success: 'SUCCESS',
  failure: 'FAILURE',
  stopped: 'STOPPED'
};

const AllStatuses = [
  Statuses.running,
  Statuses.pausing,
  Statuses.paused,
  Statuses.resuming,
  Statuses.success,
  Statuses.failure,
  Statuses.stopped
];

export function getStatusName (status) {
  switch (status) {
    case Statuses.running: return 'Running';
    case Statuses.pausing: return 'Pausing';
    case Statuses.paused: return 'Paused';
    case Statuses.resuming: return 'Resuming';
    case Statuses.success: return 'Success';
    case Statuses.failure: return 'Failure';
    case Statuses.stopped: return 'Stopped';
    default:
      return 'Unknown';
  }
}

export {AllStatuses};
export default Statuses;
