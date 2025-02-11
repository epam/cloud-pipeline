import dayjs, { Dayjs } from 'dayjs';
import { getRunStatusesTimeline, RunStatusTimelineStatus } from './run-statuses-timeline';
import { Run, RunHistoryPhase, RunInterval, RunStatuses, RunTaskInfo } from '../../model';

export type RunDurationInfo = {
  info: RunInterval;
  last: RunInterval;
  wasPaused: boolean;
  totalDuration: number; // in seconds
  totalRunningDuration: number; // in seconds
  totalBillableDuration: number; // in seconds
  totalBillableRunningDuration: number; // in seconds
  totalNonPausedDuration: number; // in seconds
  activeDuration: number; // in seconds
  pausedDuration: number; // in seconds
  schedulingDuration: number; // in seconds
  runningIntervals: RunInterval[];
  pausedIntervals: RunInterval[];
  scheduledIntervals: RunInterval[];
  scheduledDate?: Dayjs;
  runningDate?: Dayjs;
};

/**
 * Creates an interval
 */
export function getInterval(phase: RunHistoryPhase, start?: Dayjs | string, end?: Dayjs | string): RunInterval {
  const startDate = start ? dayjs.utc(start) : dayjs.utc();
  const endDate = end ? dayjs.utc(end) : undefined;
  return {
    phase,
    start: startDate,
    end: endDate,
  };
}

/**
 * Updates interval end date (in-place) and returns it
 */
export function updateIntervalEndDate(interval: RunInterval, end?: Dayjs | string): RunInterval {
  interval.end = end ? dayjs.utc(end) : undefined;
  return interval;
}

/**
 * Gets run phase by its status
 */
export function getRunPhaseByStatus(status: RunStatusTimelineStatus): RunHistoryPhase {
  switch (status.toUpperCase()) {
    case RunStatuses.failure:
    case RunStatuses.stopped:
    case RunStatuses.success:
      return RunHistoryPhase.stopped;
    case RunStatuses.paused:
      return RunHistoryPhase.paused;
    case 'SCHEDULED':
      return RunHistoryPhase.scheduled;
    default:
      return RunHistoryPhase.running;
  }
}

/**
 * @param interval - RunInterval
 * @returns number
 */
export function getIntervalDuration(interval: RunInterval): number {
  return (interval.end || dayjs.utc()).diff(interval.start, 'seconds', true);
}

/**
 * Gets intervals total duration in seconds
 * @param intervals - RunInterval[]
 * @returns number
 */
export function getIntervalsTotalDuration(intervals: RunInterval[] = []): number {
  return intervals.reduce((duration, interval) => duration + getIntervalDuration(interval), 0);
}

/**
 * @param fromDate - string
 * @param intervals - RunInterval[]
 * @param phase - RunHistoryPhase
 * @returns number
 */
export function getRunningDuration(fromDate: string | undefined, intervals: RunInterval[], ...phase: number[]): number {
  if (!fromDate) {
    return 0;
  }
  const date = dayjs.utc(fromDate);
  const filtered = intervals
    .filter((interval) => phase.includes(interval.phase))
    .filter((interval) => interval.start >= date || !interval.end || interval.end > date)
    .map((interval) => {
      const { start } = interval;
      if (start >= date) {
        return interval;
      }
      return {
        ...interval,
        start: date,
      };
    });
  return getIntervalsTotalDuration(filtered);
}

/**
 * Gets run duration info (running, paused, paused intervals etc.)
 * @param run
 * @param analyseSchedulingPhase
 * @param tasks
 * @returns RunDurationInfo | undefined
 */
export function getRunDurationInfo(
  run: Run | undefined,
  analyseSchedulingPhase = false,
  tasks: RunTaskInfo[] = [],
): RunDurationInfo | undefined {
  if (!run) {
    return undefined;
  }
  const info = getInterval(RunHistoryPhase.running, run.startDate, run.endDate);
  const timeline = getRunStatusesTimeline(run, analyseSchedulingPhase, tasks);

  const intervals: RunInterval[] = [];
  for (const timelineItem of timeline) {
    const { status, timestamp } = timelineItem;
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

  const filteredIntervals = intervals.filter((interval) => getIntervalDuration(interval) > 0);
  const runningIntervals = filteredIntervals.filter((interval) => interval.phase === RunHistoryPhase.running);
  const pausedIntervals = filteredIntervals.filter((interval) => interval.phase === RunHistoryPhase.paused);
  const scheduledIntervals = filteredIntervals.filter((interval) => interval.phase === RunHistoryPhase.scheduled);

  const totalBillableDuration = getRunningDuration(
    run.instanceStartDate,
    filteredIntervals,
    RunHistoryPhase.running,
    RunHistoryPhase.paused,
    RunHistoryPhase.scheduled,
  );
  const totalBillableRunningDuration = getRunningDuration(
    run.instanceStartDate,
    filteredIntervals,
    RunHistoryPhase.running,
    RunHistoryPhase.scheduled,
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
    totalBillableRunningDuration,
  };
}

export function displayDurationInSeconds(duration: number = 0, details: boolean = false) {
  const MINUTE = 60;
  const HOUR = 60 * MINUTE;
  const DAY = 24 * HOUR;
  const days = Math.floor(duration / DAY);
  const hours = Math.floor((duration - days * DAY) / HOUR);
  const minutes = Math.floor((duration - days * DAY - hours * HOUR) / MINUTE);
  const seconds = Math.floor(duration - days * DAY - hours * HOUR - minutes * MINUTE);
  const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`;
  const parts = [
    days > 0 ? plural(days, 'day') : undefined,
    hours > 0 ? plural(hours, 'hour') : undefined,
    minutes > 0 ? plural(minutes, 'minute') : undefined,
    plural(seconds, 'second'),
  ].filter(Boolean);
  if (details) {
    return parts.join(', ');
  }
  return parts[0];
}
