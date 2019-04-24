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

const Statuses = {
  failure: 'FAILURE',
  paused: 'PAUSED',
  pausing: 'PAUSING',
  pulling: 'PULLING',
  queued: 'QUEUED',
  resuming: 'RESUMING',
  running: 'RUNNING',
  scheduled: 'SCHEDULED',
  stopped: 'STOPPED',
  success: 'SUCCESS',

  unknown: 'unknown'
};
export const AllStatusValues = Object.values(Statuses);
export function correctStatusValue (status) {
  if (status && typeof status === 'string') {
    const index = AllStatusValues.indexOf((status || '').toUpperCase());
    if (index >= 0) {
      return AllStatusValues[index];
    }
  }
  return Statuses.unknown;
}
export function getRunStatus (run) {
  if (run) {
    let status = run.status || Statuses.unknown;
    if (status.toUpperCase() === Statuses.running &&
      run.instance &&
      (run.instance.nodeIP || run.instance.nodeName) &&
      (!run.podIP && !run.initialized)) {
      return Statuses.pulling;
    } else if (status.toUpperCase() === Statuses.running &&
      (
        !run.instance ||
        !run.instance.nodeIP ||
        !run.instance.nodeName
      ) && !run.initialized) {
      return run.queued ? Statuses.queued : Statuses.scheduled;
    }
    return run.status || Statuses.unknown;
  }
  return Statuses.unknown;
}
export function getStatus (props) {
  if (props) {
    return props.status
      ? correctStatusValue(props.status)
      : getRunStatus(props.run);
  }
  return Statuses.unknown;
}

export default Statuses;
