import dayjs, { Dayjs } from 'dayjs';
import { Run, RunTaskInfo } from '../../model';

export type RunStatusTimelineItem = {
  status: string;
  timestamp: Dayjs;
};

export function getRunStatusesTimeline(
  run: Run,
  analyseSchedulingPhase: boolean = false,
  tasks: RunTaskInfo[] = [],
): RunStatusTimelineItem[] {
  if (!run) {
    return [];
  }

  const {
    startDate: runStartDate,
    endDate: runEndDate,
    runStatuses = [],
  } = run;

  if (!runStartDate) {
    return [];
  }

  let actualRunStartDate: Dayjs | undefined;
  if (
    tasks &&
    tasks.filter((task) => !/^console$/i.test(task.name || '') && task.started)
      .length > 0
  ) {
    actualRunStartDate = tasks
      .filter((task) => !/^console$/i.test(task.name || '') && task.started)
      .map((task) => dayjs.utc(task.started))
      .sort((a, b) => (a.isAfter(b) ? -1 : a.isBefore(b) ? 1 : 0))
      .pop();
  }

  const startDate = dayjs.utc(runStartDate);
  const endDate = runEndDate ? dayjs.utc(runEndDate) : undefined;
  let actualStartDate = analyseSchedulingPhase
    ? actualRunStartDate
      ? dayjs.utc(actualRunStartDate)
      : undefined
    : undefined;

  if (actualStartDate && endDate && actualStartDate.isAfter(endDate)) {
    // Ignore the first task's date if it's after the run's termination date
    actualStartDate = undefined;
  }

  const dates: RunStatusTimelineItem[] = (runStatuses || []).map((r) => ({
    status: r.status,
    timestamp: dayjs.utc(r.timestamp),
  }));

  dates.push({
    status: 'SCHEDULED',
    timestamp: startDate,
  });

  if (actualStartDate) {
    dates.push({
      status: 'RUNNING',
      timestamp: actualStartDate,
    });
  }

  dates.sort((dA, dB) => {
    if (dA.timestamp.isAfter(dB.timestamp)) {
      return 1;
    } else if (dA.timestamp.isBefore(dB.timestamp)) {
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
      date.status = 'SCHEDULED';
    } else if (actualStartDate && date.timestamp.isBefore(actualStartDate)) {
      date.status = 'SCHEDULED';
    }
  });

  const reduced: RunStatusTimelineItem[] = dates.reduce((result, current) => {
    if (result.length === 0) {
      return [current];
    }
    if (result[result.length - 1].status === current.status) {
      return result;
    }
    return [...result, current];
  }, [] as RunStatusTimelineItem[]);

  const last = reduced[reduced.length - 1];
  if (endDate && !['STOPPED', 'SUCCESS', 'FAILURE'].includes(last.status)) {
    reduced.push({
      status: 'STOPPED',
      timestamp: endDate,
    });
  }

  return reduced;
}
