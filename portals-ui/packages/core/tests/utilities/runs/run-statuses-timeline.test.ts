import dayjs from 'dayjs';
import {
  getRunStatusesTimeline,
  RunStatusTimelineItem,
  sortRunStatusTimelineItems,
} from '../../../src/utilities/runs/run-statuses-timeline.ts';
import { RunStatuses } from '../../../src';
import { generateRun } from '../../helpers/runs.ts';

describe('sortRunStatusTimelineItem', () => {
  const status1: RunStatusTimelineItem = { status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') };
  const status2: RunStatusTimelineItem = { status: RunStatuses.running, timestamp: dayjs.utc('2025-01-02') };
  const status3: RunStatusTimelineItem = { status: RunStatuses.stopped, timestamp: dayjs.utc('2025-01-03') };

  it('should sort statuses by timestamp (1)', () => {
    const result = [status3, status2, status1].sort(sortRunStatusTimelineItems);
    expect(result).toEqual([status1, status2, status3]);
  });
  it('should sort statuses by timestamp (2)', () => {
    const result = [status1, status2, status3].sort(sortRunStatusTimelineItems);
    expect(result).toEqual([status1, status2, status3]);
  });
  it('should sort statuses by timestamp (3)', () => {
    const result = [status1, status1, status1].sort(sortRunStatusTimelineItems);
    expect(result).toEqual([status1, status1, status1]);
  });
});

describe('getRunStatusesTimeline', () => {
  it('should return an empty array when runStatuses is empty and runStartDate is available', () => {
    const run = generateRun({ startDate: '2025-01-01', runStatuses: [] });
    expect(getRunStatusesTimeline(run)).toEqual([{ status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') }]);
  });

  it('should include scheduled and running statuses when tasks have started', () => {
    const run = generateRun({ startDate: '2025-01-01', runStatuses: [] });
    const tasks = [{ name: 'task1', started: '2025-01-02T00:00:00Z' }];
    const result = getRunStatusesTimeline(run, true, tasks);
    expect(result).toEqual([
      { status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') },
      { status: 'RUNNING', timestamp: dayjs.utc('2025-01-02T00:00:00Z') },
    ]);
  });

  it('should ignore tasks if their start date is after endDate', () => {
    const run = generateRun({ startDate: '2025-01-01', endDate: '2025-01-02', runStatuses: [] });
    const tasks = [{ name: 'task1', started: '2025-01-03T00:00:00Z' }];
    const result = getRunStatusesTimeline(run, true, tasks);
    expect(result).toEqual([
      { status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') },
      { status: 'STOPPED', timestamp: dayjs.utc('2025-01-02') },
    ]);
  });

  it('should handle analyseSchedulingPhase correctly', () => {
    const run = generateRun({ startDate: '2025-01-01', runStatuses: [] });
    const tasks = [{ name: 'task1', started: '2025-01-02T00:00:00Z' }];

    const result = getRunStatusesTimeline(run, true, tasks);
    expect(result).toEqual([
      { status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') },
      { status: 'RUNNING', timestamp: dayjs.utc('2025-01-02T00:00:00Z') },
    ]);
  });

  it('should remove duplicate statuses', () => {
    const run = generateRun({
      startDate: '2025-01-01',
      runStatuses: [
        { status: RunStatuses.running, timestamp: '2025-01-01' },
        { status: RunStatuses.running, timestamp: '2025-01-01' },
      ],
    });

    const result = getRunStatusesTimeline(run);
    expect(result).toEqual([
      { status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') },
      { status: 'RUNNING', timestamp: dayjs.utc('2025-01-01') },
    ]);
  });

  it('should add STOPPED status when endDate is present and last status is not STOPPED, SUCCESS or FAILURE', () => {
    const run = generateRun({
      startDate: '2025-01-01',
      endDate: '2025-01-04',
      runStatuses: [{ status: RunStatuses.running, timestamp: '2025-01-02' }],
    });

    const result = getRunStatusesTimeline(run);
    expect(result).toEqual([
      { status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') },
      { status: RunStatuses.running, timestamp: dayjs.utc('2025-01-02') },
      { status: 'STOPPED', timestamp: dayjs.utc('2025-01-04') },
    ]);
  });

  it('should ignore run statuses after endDate', () => {
    const run = generateRun({
      startDate: '2025-01-01',
      endDate: '2025-01-04',
      runStatuses: [
        { status: RunStatuses.running, timestamp: '2025-01-02' },
        { status: RunStatuses.running, timestamp: '2025-01-05' },
      ],
    });

    const result = getRunStatusesTimeline(run);
    expect(result).toEqual([
      { status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') },
      { status: RunStatuses.running, timestamp: dayjs.utc('2025-01-02') },
      { status: 'STOPPED', timestamp: dayjs.utc('2025-01-04') },
    ]);
  });

  it('should handle no tasks and endDate correctly', () => {
    const run = generateRun({
      startDate: '2025-01-01',
      endDate: '2025-01-04',
      runStatuses: [{ status: RunStatuses.running, timestamp: '2025-01-02' }],
    });

    const result = getRunStatusesTimeline(run);
    expect(result).toEqual([
      { status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') },
      { status: 'RUNNING', timestamp: dayjs.utc('2025-01-02') },
      { status: 'STOPPED', timestamp: dayjs.utc('2025-01-04') },
    ]);
  });

  it('should return SCHEDULED state only for run without tasks and end date', () => {
    const run = generateRun({
      startDate: '2025-01-01',
      runStatuses: [
        { status: RunStatuses.running, timestamp: '2025-01-02' },
        { status: RunStatuses.pausing, timestamp: '2025-01-03' },
        { status: RunStatuses.paused, timestamp: '2025-01-04' },
        { status: RunStatuses.resuming, timestamp: '2025-01-05' },
        { status: RunStatuses.running, timestamp: '2025-01-06' },
      ],
    });

    const tasks = [{ name: 'console', started: '2025-01-01T00:00:00Z' }, { name: 'task-without-start-date' }];

    const result = getRunStatusesTimeline(run, true, tasks);
    expect(result).toEqual([{ status: 'SCHEDULED', timestamp: dayjs.utc('2025-01-01') }]);
  });
});
