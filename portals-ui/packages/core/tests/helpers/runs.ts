import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import { AclClass, CommitStatuses, Run, RunStatuses, RunTaskInfo } from '../../src';

dayjs.extend(utc);

export function generateRun(run: Partial<Run>): Run {
  return {
    id: 1,
    mask: 15,
    createdDate: '2025-01-01T10:00:00.000Z',
    locked: false,
    owner: 'owner',
    originalOwner: 'owner',
    startDate: '2025-01-01T10:00:00.000Z',
    instanceStartDate: run.startDate ?? '2025-01-01T10:00:00.000Z',
    status: RunStatuses.running,
    commitStatus: CommitStatuses.notCommitted,
    dockerImage: 'docker-image',
    actualDockerImage: 'docker-image',
    platform: 'linux',
    cmdTemplate: 'cmd',
    actualCmd: 'cmd',
    terminating: false,
    sensitive: false,
    podId: 'pod-id',
    instance: {},
    timeout: 0,
    nodeCount: 0,
    initialized: true,
    configurationId: 0,
    pricePerHour: 0,
    computePricePerHour: 0,
    diskPricePerHour: 0,
    nonPause: false,
    aclClass: AclClass.pipeline,
    kubeServiceEnabled: false,
    workerRun: false,
    clusterRun: false,
    taskName: 'task-name',
    masterRun: false,
    ...run,
  };
}

const HOUR_IN_SECONDS = 3600;

/**
 * Generates a sample run with specified running and paused durations.
 * The paused duration is split into 1-hour chunks.
 * @param runningSeconds - Total duration of the run.
 * @param pausedSeconds - Total duration of the run in "paused" status.
 * @param run - run partial config
 * @returns an object {run, runTasks}
 */
export function generateSampleRun(
  runningSeconds: number,
  pausedSeconds: number,
  run: Partial<Run>,
): { run: Run; tasks: RunTaskInfo[] } {
  if (runningSeconds < pausedSeconds) {
    throw new Error('wrong sample run config: running time should be greater than paused time');
  }
  const startDate = dayjs.utc(run.startDate ?? '2025-02-01T00:00:00Z');
  const taskStartDate = startDate.add(Math.min(runningSeconds / 2.0, 1800), 'second');
  const runStatuses = [];

  let currentTimestamp = startDate;

  // Start with a running status
  runStatuses.push({ status: RunStatuses.running, timestamp: currentTimestamp.toISOString() });

  const pauseChunksCount = Math.ceil(pausedSeconds / HOUR_IN_SECONDS);
  if (pauseChunksCount > 0) {
    const runDuration = (runningSeconds - pausedSeconds) / pauseChunksCount;
    const pauseDuration = pausedSeconds / pauseChunksCount;

    for (let i = 0; i < pauseChunksCount; i += 1) {
      currentTimestamp = currentTimestamp.add(runDuration, 'second');
      runStatuses.push({ status: RunStatuses.paused, timestamp: currentTimestamp.toISOString() });
      currentTimestamp = currentTimestamp.add(pauseDuration, 'second');
      runStatuses.push({ status: RunStatuses.running, timestamp: currentTimestamp.toISOString() });
    }
  } else {
    currentTimestamp = startDate.add(runningSeconds, 'second');
  }

  return {
    run: generateRun({
      ...run,
      startDate: startDate.toISOString(),
      instanceStartDate: startDate.toISOString(),
      endDate: currentTimestamp.toISOString(),
      runStatuses,
    }),
    tasks: [{ name: 'non-console', started: taskStartDate.toISOString() }],
  };
}

const second = 1;
const minute = 60 * second;
const hour = 60 * minute;

type RunDurationTestCaseConfig = {
  runningDuration: number;
  pausedDuration: number;
  computePricePerHour: number;
  diskPricePerHour: number;
  workersPrice: number;
};

type RunDurationTestCase = RunDurationTestCaseConfig & {
  run: Run;
  tasks: RunTaskInfo[];
  workersCosts: number;
  masterCosts: number;
  totalCosts: number;
  description: string;
};

function getTestConfigDescription(config: RunDurationTestCaseConfig): string {
  const { runningDuration, pausedDuration, computePricePerHour, diskPricePerHour, workersPrice: workers } = config;
  return `${runningDuration}sec. running, ${pausedDuration}sec. paused, ${computePricePerHour} compute price, ${diskPricePerHour} disk price, ${workers} workers price`;
}

function generateRunDurationTestCase(config: RunDurationTestCaseConfig): RunDurationTestCase {
  const { runningDuration, pausedDuration, computePricePerHour, diskPricePerHour, workersPrice: workers } = config;
  const master =
    (runningDuration / hour - pausedDuration / hour) * computePricePerHour +
    (runningDuration / hour) * diskPricePerHour;
  const total = master + workers;
  const format = (value: number): number => Math.ceil(value * 100.0) / 100.0;
  return {
    ...config,
    ...generateSampleRun(config.runningDuration, config.pausedDuration, {
      diskPricePerHour,
      computePricePerHour,
      workersPrice: workers,
    }),
    workersCosts: format(workers),
    masterCosts: format(master),
    totalCosts: format(total),
    description: getTestConfigDescription(config),
  };
}

const runDurationTestCaseConfigs: RunDurationTestCaseConfig[] = [
  {
    runningDuration: hour,
    pausedDuration: 30 * minute,
    computePricePerHour: 15,
    diskPricePerHour: 10,
    workersPrice: 25,
  },
  {
    runningDuration: 2 * hour,
    pausedDuration: hour + 30 * minute,
    computePricePerHour: 15,
    diskPricePerHour: 10,
    workersPrice: 25,
  },
  {
    runningDuration: 2 * hour,
    pausedDuration: 0,
    computePricePerHour: 1,
    diskPricePerHour: 1,
    workersPrice: 1,
  },
];
const runDurationTestCases = runDurationTestCaseConfigs.map(generateRunDurationTestCase);

export { runDurationTestCases };
