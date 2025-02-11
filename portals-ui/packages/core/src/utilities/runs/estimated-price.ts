import { getRunDurationInfo } from './run-duration';
import { Run, RunTaskInfo } from '../../model';

const SECONDS_IN_HOUR = 3600;

export type RunPriceEstimationOptions = {
  analyseSchedulingPhase?: boolean;
  runTasks?: RunTaskInfo[];
};

export type RunPriceEstimationResult = {
  total: number;
  master: number;
  workers: number;
};

export function evaluateRunPrice(
  run: Run | undefined,
  options: RunPriceEstimationOptions = {},
): RunPriceEstimationResult {
  const { analyseSchedulingPhase = false, runTasks = [] } = options;

  const { totalBillableDuration, totalBillableRunningDuration } = getRunDurationInfo(
    run,
    analyseSchedulingPhase,
    runTasks,
  ) || {
    totalBillableDuration: 0,
    totalBillableRunningDuration: 0,
  };

  const { computePricePerHour = 0, diskPricePerHour = 0, workersPrice = 0 } = run ?? {};

  const format = (value: number): number => Math.ceil(value * 100.0) / 100.0;

  const master =
    computePricePerHour * (totalBillableRunningDuration / SECONDS_IN_HOUR) +
    diskPricePerHour * (totalBillableDuration / SECONDS_IN_HOUR);

  return {
    master: format(master),
    workers: format(workersPrice),
    total: format(master + workersPrice),
  };
}
