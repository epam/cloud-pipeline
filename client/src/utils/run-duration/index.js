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
import getRunStatusesTimeline from './run-statuses-timeline';

const RunHistoryPhase = {
  scheduled: 0,
  running: 1,
  paused: 2,
  stopped: 3
};

export {RunHistoryPhase};

/**
 * @typedef {Object} RunInterval
 * @property {number} phase - 0 - scheduling, 1 - running, 2 - paused, 3 - stopped
 * @property {moment.Moment} start - interval start date
 * @property {moment.Moment} end - interval end date
 */

/**
 * @typedef {Object} RunDurationInfo
 * @property {RunInterval} info - total interval info
 * @property {RunInterval} last - last interval info
 * @property {boolean} wasPaused - if run was ever paused
 * @property {number} totalDuration - total duration (from job submission) in seconds
 * @property {number} totalRunningDuration - total running duration in seconds
 * @property {number} totalBillableDuration - total billable duration in seconds
 * @property {number} totalBillableRunningDuration - total billable and running duration in seconds
 * (duration after initialization, including paused intervals)
 * @property {number} totalNonPausedDuration - total non-paused duration in seconds
 * (scheduling stages, running stages)
 * @property {number} activeDuration - active duration (when run was in "RUNNING" state) in seconds
 * @property {number} pausedDuration - paused duration (when run was in "PAUSED" state) in seconds
 * @property {number} schedulingDuration - scheduling duration in seconds
 * @property {RunInterval[]} runningIntervals - running intervals
 * @property {RunInterval[]} pausedIntervals - paused intervals
 * @property {RunInterval[]} scheduledIntervals - paused intervals
 * @property {moment.Moment} scheduledDate
 * @property {moment.Moment} runningDate
 */

/**
 * Creates interval
 * @param {number} phase - 0 - scheduled, 1 - running, 2 - paused, 3 - stopped
 * @param {moment.Moment | string} [start]
 * @param {moment.Moment | string} [end]
 * @returns {RunInterval}
 */
function getInterval (phase, start, end) {
  const startDate = start ? moment.utc(start) : moment.utc();
  const endDate = end ? moment.utc(end) : undefined;
  return {
    phase,
    start: startDate,
    end: endDate
  };
}

/**
 * Updates interval end date (in-place) and returns it
 * @param {RunInterval} interval
 * @param {moment.Moment | string | undefined} [end]
 * @returns {RunInterval}
 */
function updateIntervalEndDate (interval, end) {
  interval.end = end ? moment.utc(end) : undefined;
  return interval;
}

/**
 * Gets run phase by its status
 * @param {string} status - run status
 * @returns {number}
 */
function getRunPhaseByStatus (status) {
  switch ((status || '').toUpperCase()) {
    case 'FAILURE':
    case 'STOPPED':
    case 'SUCCESS':
      return RunHistoryPhase.stopped;
    case 'PAUSED':
      return RunHistoryPhase.paused;
    case 'SCHEDULED':
      return RunHistoryPhase.scheduled;
    default:
      return RunHistoryPhase.running;
  }
}

/**
 * @param {RunInterval} interval
 * @returns {number}
 */
export function getIntervalDuration (interval) {
  return (interval.end || moment.utc()).diff(interval.start, 'seconds', true);
}

/**
 * Gets intervals total duration in seconds
 * @param {RunInterval[]} intervals
 * @returns {number}
 */
function getIntervalsTotalDuration (intervals = []) {
  return intervals
    .reduce((duration, interval) => duration + getIntervalDuration(interval), 0);
}

/**
 * @param {string} fromDate
 * @param {RunInterval[]} intervals
 * @param {RunHistoryPhase} phase
 * @returns {number}
 */
function getRunningDuration (fromDate, intervals, ...phase) {
  if (!fromDate) {
    return 0;
  }
  const date = moment.utc(fromDate);
  const filtered = (intervals || [])
    .filter((interval) => phase.includes(interval.phase))
    .filter((interval) => interval.start >= date || (!interval.end || interval.end > date))
    .map((interval) => {
      const {
        start
      } = interval;
      if (start >= date) {
        return interval;
      }
      return {
        ...interval,
        start: date
      };
    });
  return getIntervalsTotalDuration(filtered);
}

/**
 * Gets run duration info (running, paused, paused intervals etc.)
 * @param {RunInfo} run
 * @param {boolean} [analyseSchedulingPhase=false]
 * @param {RunTaskInfo[]} [tasks]
 * @returns {RunDurationInfo | undefined}
 */
export default function getRunDurationInfo (
  run,
  analyseSchedulingPhase = false,
  tasks = []
) {
  if (!run) {
    return undefined;
  }
  const info = getInterval(RunHistoryPhase.running, run.startDate, run.endDate);
  const timeline = getRunStatusesTimeline(run, analyseSchedulingPhase, tasks);
  /**
   * @type {RunInterval[]}
   */
  const intervals = [];
  for (const timelineItem of timeline) {
    const {
      status,
      timestamp
    } = timelineItem;
    const phase = getRunPhaseByStatus(status);
    const previous = intervals.length > 0 ? intervals[intervals.length - 1] : undefined;
    const current = getInterval(phase, timestamp);
    if (previous && previous.phase !== current.phase) {
      updateIntervalEndDate(previous, timestamp);
    }
    if (!previous || previous.phase !== current.phase) {
      intervals.push(current);
    }
  }
  const filteredIntervals = intervals
    .filter((interval) => getIntervalDuration(interval) > 0);
  const runningIntervals = filteredIntervals
    .filter((interval) => interval.phase === RunHistoryPhase.running);
  const pausedIntervals = filteredIntervals
    .filter((interval) => interval.phase === RunHistoryPhase.paused);
  const scheduledIntervals = filteredIntervals
    .filter((interval) => interval.phase === RunHistoryPhase.scheduled);
  const totalBillableDuration = getRunningDuration(
    run.instanceStartDate,
    filteredIntervals,
    RunHistoryPhase.running,
    RunHistoryPhase.paused,
    RunHistoryPhase.scheduled
  );
  const totalBillableRunningDuration = getRunningDuration(
    run.instanceStartDate,
    filteredIntervals,
    RunHistoryPhase.running,
    RunHistoryPhase.scheduled
  );
  const activeDuration = getIntervalsTotalDuration(runningIntervals);
  const pausedDuration = getIntervalsTotalDuration(pausedIntervals);
  const schedulingDuration = getIntervalsTotalDuration(scheduledIntervals);
  const [runningInterval] = runningIntervals;
  const [scheduledInterval = info] = scheduledIntervals;
  const runningDate = runningInterval ? runningInterval.start : undefined;
  const scheduledDate = scheduledInterval.start;
  return {
    info,
    last: intervals[intervals.length - 1],
    wasPaused: pausedIntervals.length > 0,
    totalDuration: activeDuration + pausedDuration + schedulingDuration,
    totalRunningDuration: activeDuration + pausedDuration,
    activeDuration,
    pausedDuration,
    schedulingDuration,
    totalNonPausedDuration: activeDuration + schedulingDuration,
    pausedIntervals,
    runningIntervals,
    scheduledIntervals,
    runningDate,
    scheduledDate,
    totalBillableDuration,
    totalBillableRunningDuration
  };
}
