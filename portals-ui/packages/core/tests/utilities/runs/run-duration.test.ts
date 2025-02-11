import dayjs from 'dayjs';
import { displayDurationInSeconds, getRunDurationInfo, RunHistoryPhase, RunStatuses, RunTaskInfo } from '../../../src';
import { generateRun, runDurationTestCases } from '../../helpers/runs.ts';
import {
  getInterval,
  getIntervalDuration,
  getIntervalsTotalDuration,
  getRunningDuration,
  getRunPhaseByStatus,
  updateIntervalEndDate,
} from '../../../src/utilities/runs/run-duration.ts';

const second = 1;
const minute = 60 * second;
const hour = 60 * minute;
const day = 24 * hour;

describe('Run Duration Utilities', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });
  describe('getInterval', () => {
    it('should return the correct interval with start and end', () => {
      const start = dayjs.utc('2025-02-01T10:00:00Z');
      const end = dayjs.utc('2025-02-01T12:00:00Z');
      const interval = getInterval(RunHistoryPhase.running, start, end);
      expect(interval.start.isSame(start)).toBe(true);
      expect(interval.end?.isSame(end)).toBe(true);
      expect(interval.phase).toBe(RunHistoryPhase.running); // Running phase
    });

    it('should return the current time as start if no start is provided', () => {
      jest.doMock('dayjs', () => {
        const actualDayjs = jest.requireActual('dayjs');
        return {
          ...actualDayjs,
          utc: jest.fn(() => actualDayjs('2025-02-01T15:00:00.000Z')),
        };
      });
      const interval = getInterval(RunHistoryPhase.scheduled); // Scheduled phase
      expect(interval.start.isSame(dayjs.utc())).toBe(true);
    });
  });

  describe('updateIntervalEndDate', () => {
    it('should update the end date of the interval', () => {
      const interval = getInterval(RunHistoryPhase.running, '2025-02-01T10:00:00Z', '2025-02-01T12:00:00Z');
      const newEnd = dayjs.utc('2025-02-01T13:00:00Z');
      const updatedInterval = updateIntervalEndDate(interval, newEnd);
      expect(updatedInterval.end?.isSame(newEnd)).toBe(true);
    });
    it('should unset the end date of the interval', () => {
      const interval = getInterval(RunHistoryPhase.running, '2025-02-01T10:00:00Z', '2025-02-01T12:00:00Z');
      const updatedInterval = updateIntervalEndDate(interval, undefined);
      expect(updatedInterval.end).toBe(undefined);
    });
  });

  describe('getRunPhaseByStatus', () => {
    it('should return correct phases for different statuses', () => {
      expect(getRunPhaseByStatus(RunStatuses.running)).toBe(RunHistoryPhase.running); // Running phase
      expect(getRunPhaseByStatus(RunStatuses.paused)).toBe(RunHistoryPhase.paused); // Paused phase
      expect(getRunPhaseByStatus('SCHEDULED')).toBe(RunHistoryPhase.scheduled); // Scheduled phase
      expect(getRunPhaseByStatus(RunStatuses.stopped)).toBe(RunHistoryPhase.stopped); // Stopped phase
      expect(getRunPhaseByStatus(RunStatuses.success)).toBe(RunHistoryPhase.stopped); // Stopped phase
      expect(getRunPhaseByStatus(RunStatuses.failure)).toBe(RunHistoryPhase.stopped); // Stopped phase
    });
  });

  describe('getIntervalDuration', () => {
    it('should return the correct duration in seconds', () => {
      const interval = getInterval(RunHistoryPhase.paused, '2025-02-01T10:00:00Z', '2025-02-01T12:00:00Z');
      const duration = getIntervalDuration(interval);
      expect(duration).toBe(2 * hour); // 2 hours in seconds
    });
  });

  describe('getIntervalsTotalDuration', () => {
    it('should calculate the total duration of intervals correctly', () => {
      const intervals = [
        getInterval(RunHistoryPhase.running, '2025-02-01T10:00:00Z', '2025-02-01T12:00:00Z'),
        getInterval(RunHistoryPhase.paused, '2025-02-01T12:00:00Z', '2025-02-01T14:00:00Z'),
      ];
      const totalDuration = getIntervalsTotalDuration(intervals);
      expect(totalDuration).toBe(4 * hour); // 4 hours in seconds
    });
  });

  describe('getRunningDuration', () => {
    it('should return correct running duration based on intervals', () => {
      const intervals = [
        getInterval(RunHistoryPhase.paused, '2025-02-01T10:00:00Z', '2025-02-01T12:00:00Z'),
        getInterval(RunHistoryPhase.running, '2025-02-01T12:00:00Z', '2025-02-01T14:00:00Z'),
      ];
      const runningDuration = getRunningDuration('2025-02-01T10:00:00Z', intervals, RunHistoryPhase.paused);
      expect(runningDuration).toBe(2 * hour); // Total running duration in seconds
    });
    it('should return 0 if from date is not provided', () => {
      const intervals = [
        getInterval(RunHistoryPhase.paused, '2025-02-01T10:00:00Z', '2025-02-01T12:00:00Z'),
        getInterval(RunHistoryPhase.running, '2025-02-01T12:00:00Z', '2025-02-01T14:00:00Z'),
      ];
      const runningDuration = getRunningDuration(undefined, intervals, RunHistoryPhase.paused);
      expect(runningDuration).toBe(0);
    });
    it('should handle intervals before fromDate correctly', () => {
      const intervals = [
        getInterval(RunHistoryPhase.running, '2025-02-01T09:00:00Z', '2025-02-01T12:00:00Z'),
        getInterval(RunHistoryPhase.paused, '2025-02-01T12:00:00Z', '2025-02-01T13:00:00Z'),
        getInterval(RunHistoryPhase.running, '2025-02-01T13:00:00Z', '2025-02-01T14:00:00Z'),
      ];
      const runningDuration = getRunningDuration('2025-02-01T10:00:00Z', intervals, RunHistoryPhase.running);
      expect(runningDuration).toBe(3 * hour);
    });
  });

  describe('getRunDurationInfo', () => {
    it('should return undefined if no run is provided', () => {
      const runningDuration = getRunDurationInfo(undefined);
      expect(runningDuration).toBe(undefined);
    });
    it('should return the correct run duration information', () => {
      const run = generateRun({
        startDate: '2025-02-01T10:00:00Z',
        endDate: '2025-02-01T14:00:00Z',
        instanceStartDate: '2025-02-01T09:00:00Z',
        runStatuses: [
          {
            status: RunStatuses.pausing,
            timestamp: '2025-02-01T11:30:00Z',
          },
          {
            status: RunStatuses.paused,
            timestamp: '2025-02-01T12:00:00Z',
          },
          {
            status: RunStatuses.resuming,
            timestamp: '2025-02-01T13:00:00Z',
          },
          {
            status: RunStatuses.running,
            timestamp: '2025-02-01T13:30:00Z',
          },
        ],
      });
      const taskStartDate = '2025-02-01T11:00:00Z';
      const tasks: RunTaskInfo[] = [{ name: 'non-console', started: taskStartDate }];
      const result = getRunDurationInfo(run, true, tasks);
      expect(result?.totalDuration).toBe(4 * hour);
      expect(result?.activeDuration).toBe(2 * hour);
      expect(result?.runningDate).not.toBe(undefined);
      expect(result?.runningDate!.isSame(dayjs.utc(taskStartDate))).toBe(true);
      expect(result?.pausedDuration).toBe(hour); // No paused duration
    });
    it('should not return runningDate for non-started runs', () => {
      const run = generateRun({
        startDate: '2025-02-01T10:00:00Z',
        instanceStartDate: '2025-02-01T09:00:00Z',
        runStatuses: [
          {
            status: RunStatuses.pausing,
            timestamp: '2025-02-01T11:30:00Z',
          },
          {
            status: RunStatuses.paused,
            timestamp: '2025-02-01T12:00:00Z',
          },
          {
            status: RunStatuses.resuming,
            timestamp: '2025-02-01T13:00:00Z',
          },
          {
            status: RunStatuses.running,
            timestamp: '2025-02-01T13:30:00Z',
          },
        ],
      });
      const taskStartDate = '2025-02-01T11:00:00Z';
      const tasks: RunTaskInfo[] = [{ name: 'console', started: taskStartDate }];
      const result = getRunDurationInfo(run, true, tasks);
      expect(result?.runningDate).toBe(undefined);
    });
  });

  describe('displayDurationInSeconds', () => {
    it('should format duration correctly', () => {
      expect(displayDurationInSeconds(hour)).toBe('1 hour'); // 3600 seconds = 1 hour
      expect(displayDurationInSeconds(day)).toBe('1 day'); // 86400 seconds = 1 day
      expect(displayDurationInSeconds(4 * hour + minute + second, false)).toBe('4 hours');
      expect(displayDurationInSeconds(hour + minute + second, true)).toBe('1 hour, 1 minute, 1 second');
    });

    it('should format duration with details', () => {
      expect(displayDurationInSeconds(hour + minute + second, true)).toBe('1 hour, 1 minute, 1 second');
    });
  });
});

describe('should calculate correct run durations', () => {
  describe('running duration', () => {
    for (const testCase of runDurationTestCases) {
      it(`Run: ${testCase.description}`, () => {
        const result = getRunDurationInfo(testCase.run);
        expect(result?.totalDuration).not.toBe(undefined);
        expect(result?.totalDuration).toEqual(testCase.runningDuration);
      });
      it(`Run with tasks: ${testCase.description}`, () => {
        const result = getRunDurationInfo(testCase.run, true, testCase.tasks);
        expect(result?.totalDuration).not.toBe(undefined);
        expect(result?.totalDuration).toEqual(testCase.runningDuration);
      });
    }
  });

  describe('non paused duration', () => {
    for (const testCase of runDurationTestCases) {
      it(`Run: ${testCase.description}`, () => {
        const result = getRunDurationInfo(testCase.run);
        expect(result?.totalNonPausedDuration).not.toBe(undefined);
        expect(result?.totalNonPausedDuration).toEqual(testCase.runningDuration - testCase.pausedDuration);
      });
      it(`Run with tasks: ${testCase.description}`, () => {
        const result = getRunDurationInfo(testCase.run, true, testCase.tasks);
        expect(result?.totalNonPausedDuration).not.toBe(undefined);
        expect(result?.totalNonPausedDuration).toEqual(testCase.runningDuration - testCase.pausedDuration);
      });
    }
  });

  describe('paused duration', () => {
    for (const testCase of runDurationTestCases) {
      it(`Run: ${testCase.description}`, () => {
        const result = getRunDurationInfo(testCase.run);
        expect(result?.totalNonPausedDuration).not.toBe(undefined);
        expect(result?.totalDuration).not.toBe(undefined);
        expect((result?.totalDuration ?? 0) - (result?.totalNonPausedDuration ?? 0)).toEqual(testCase.pausedDuration);
      });
      it(`Run with tasks: ${testCase.description}`, () => {
        const result = getRunDurationInfo(testCase.run, true, testCase.tasks);
        expect(result?.totalNonPausedDuration).not.toBe(undefined);
        expect(result?.totalDuration).not.toBe(undefined);
        expect((result?.totalDuration ?? 0) - (result?.totalNonPausedDuration ?? 0)).toEqual(testCase.pausedDuration);
      });
    }
  });
});
