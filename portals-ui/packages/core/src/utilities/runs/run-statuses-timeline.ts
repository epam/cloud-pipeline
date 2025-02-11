import dayjs, { Dayjs } from 'dayjs';
import { Run, RunStatuses, RunTaskInfo } from '../../model';

export type RunStatusTimelineStatus = RunStatuses | 'SCHEDULED';

export type RunStatusTimelineItem = {
  status: RunStatusTimelineStatus;
  timestamp: Dayjs;
};

export function sortRunStatusTimelineItems(a: RunStatusTimelineItem, b: RunStatusTimelineItem) {
  return a.timestamp.unix() - b.timestamp.unix();
}

export function getRunStatusesTimeline(
  run: Run,
  analyseSchedulingPhase: boolean = false,
  tasks: RunTaskInfo[] = [],
): RunStatusTimelineItem[] {
  const { startDate: runScheduledDate, endDate: runEndDate, runStatuses = [] } = run;

  const scheduledDate = dayjs.utc(runScheduledDate);
  const endDate = runEndDate ? dayjs.utc(runEndDate) : undefined;

  let startDate: Dayjs | undefined;
  if (analyseSchedulingPhase) {
    // We're the first non-console task with existing start date, that are before `endDate` (if `endDate` is set) -
    // that will be a run actual start date.
    startDate = tasks
      .filter((task) => !/^console$/i.test(task.name) && task.started)
      .map((task: RunTaskInfo) => dayjs.utc(task.started))
      .filter((taskStartDate) => !endDate || endDate.isAfter(dayjs.utc(taskStartDate)))
      // sorting descending, from newest date to oldest
      .sort((a, b) => b.unix() - a.unix())
      // returning the oldest date ("the first started task date")
      .pop();
  }

  let dates: RunStatusTimelineItem[] = runStatuses.map((r) => ({
    status: r.status,
    timestamp: dayjs.utc(r.timestamp),
  }));

  if (startDate && !dates.some((dt) => dt.timestamp.isSame(startDate))) {
    dates.push({
      status: RunStatuses.running,
      timestamp: startDate,
    });
  }

  dates.sort(sortRunStatusTimelineItems);

  const scheduledTimelineItem: RunStatusTimelineItem = {
    status: 'SCHEDULED',
    timestamp: scheduledDate,
  };

  dates = [scheduledTimelineItem].concat(dates);

  dates.forEach((date) => {
    if (analyseSchedulingPhase && !startDate && !endDate) {
      date.status = 'SCHEDULED';
    } else if (startDate && date.timestamp.isBefore(startDate)) {
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
      status: RunStatuses.stopped,
      timestamp: endDate,
    });
  }

  return reduced;
}
