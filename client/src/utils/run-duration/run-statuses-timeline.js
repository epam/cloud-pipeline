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

/**
 * @typedef {object} RunStatusInfo
 * @property {string} status
 * @property {string} timestamp
 */

/**
 * @typedef {object} RunStatusTimelineItem
 * @property {string} status
 * @property {moment.Moment} timestamp
 */

/**
 * @typedef {object} RunInfo
 * @property {string} [startDate]
 * @property {string} [endDate]
 * @property {string} [instanceStartDate]
 * @property {string} [status]
 * @property {RunStatusInfo[]} [runStatuses]
 */

/**
 * @typedef {object} RunTaskInfo
 * @property {string} [started]
 */

/**
 * @param {RunInfo} run
 * @param {boolean} [analyseSchedulingPhase=false]
 * @param {RunTaskInfo[]} [tasks=[]]
 * @returns {RunStatusTimelineItem[]}
 */
export default function getRunStatusesTimeline (
  run,
  analyseSchedulingPhase = false,
  tasks = []
) {
  if (!run) {
    return [];
  }
  const {
    startDate: runStartDate,
    endDate: runEndDate,
    runStatuses = []
  } = run;
  if (!runStartDate) {
    return [];
  }
  let actualRunStartDate;
  if (tasks && tasks.filter((task) => !/^console$/i.test(task.name) && task.started).length > 0) {
    actualRunStartDate = tasks
      .filter((task) => !/^console$/i.test(task.name) && task.started)
      .map((task) => moment.utc(task.started))
      .sort((a, b) => {
        if (a > b) {
          return -1;
        }
        if (a < b) {
          return 1;
        }
        return 0;
      }).pop();
  }
  const startDate = moment.utc(runStartDate);
  const endDate = runEndDate ? moment.utc(runEndDate) : undefined;
  let actualStartDate = analyseSchedulingPhase
    ? (actualRunStartDate ? moment.utc(actualRunStartDate) : undefined)
    : undefined;
  if (actualStartDate && endDate && actualStartDate > endDate) {
    // We've received the first task, and run is stopped, but
    // the first task is after run termination date (???) -
    // we should ignore the first task's date
    actualStartDate = undefined;
  }
  const dates = (runStatuses || [])
    .map(r => ({
      status: r.status,
      timestamp: moment.utc(r.timestamp)
    }));
  dates.push({
    status: 'SCHEDULED',
    timestamp: startDate
  });
  if (actualStartDate) {
    dates.push({
      status: 'RUNNING',
      timestamp: actualStartDate
    });
  }
  dates.sort((dA, dB) => {
    if (dA.timestamp > dB.timestamp) {
      return 1;
    } else if (dA.timestamp < dB.timestamp) {
      return -1;
    }
    if (dA.status === 'SCHEDULED') {
      return -1;
    }
    if (dB.status === 'SCHEDULED') {
      return 1;
    }
    return 0;
  });
  dates.forEach((date) => {
    if (analyseSchedulingPhase && !actualStartDate && !endDate) {
      // We don't receive the first task, and the run is not stopped / finished,
      // so - it is in the initialization phase
      date.status = 'SCHEDULED';
    } else if (actualStartDate && date.timestamp < actualStartDate) {
      // We have actualStartDate (i.e. we've received first task) and the
      // current timeline point is before this date - so it was in
      // initialization phase
      date.status = 'SCHEDULED';
    }
  });
  const reduced = dates.reduce((result, current) => {
    if (result.length === 0) {
      return [current];
    }
    if (result[result.length - 1].status === current.status) {
      return result;
    }
    return [...result, current];
  }, []);
  const last = reduced[reduced.length - 1];
  if (
    endDate &&
    !['STOPPED', 'SUCCESS', 'FAILURE'].includes(last.status)
  ) {
    reduced.push({
      status: 'STOPPED',
      timestamp: endDate
    });
  }
  return reduced;
}
