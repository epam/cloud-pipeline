import { evaluateRunPrice, getRunDurationInfo, RunPriceEstimationResult } from '../../../src';
import { runDurationTestCases } from '../../helpers/runs.ts';

for (const testCase of runDurationTestCases) {
  const o = getRunDurationInfo(testCase.run, true, testCase.tasks);
  if (o) {
    console.log(
      'run',
      testCase.run.startDate,
      '-',
      testCase.run.endDate,
      'initialized:',
      testCase.run.instanceStartDate,
    );
    for (const st of testCase.run.runStatuses ?? []) {
      console.log(st.status, st.timestamp);
    }
    console.log(
      'running',
      testCase.runningDuration,
      'paused',
      testCase.pausedDuration,
      'non-paused',
      testCase.runningDuration - testCase.pausedDuration,
      '-',
      'running total:',
      o.totalRunningDuration,
      'billable total:',
      o.totalBillableDuration,
      'billable running:',
      o.totalBillableRunningDuration,
      'paused:',
      o.pausedDuration,
    );
  }
}

describe('evaluateRunPrice', () => {
  afterEach(() => {
    jest.resetAllMocks();
  });

  it('should return 0 for all values when run is undefined', () => {
    const result: RunPriceEstimationResult = evaluateRunPrice(undefined);
    expect(result).toEqual({ total: 0, master: 0, workers: 0 });
  });

  describe('should calculate the correct price based on billable durations and prices', () => {
    for (const testCase of runDurationTestCases) {
      it(`Run: ${testCase.description}`, () => {
        const result: RunPriceEstimationResult = evaluateRunPrice(testCase.run);
        const { totalCosts: total, masterCosts: master, workersCosts: workers } = testCase;
        expect(result).toEqual({ master, workers, total });
      });
      it(`Run with tasks: ${testCase.description}`, () => {
        const result: RunPriceEstimationResult = evaluateRunPrice(testCase.run, {
          analyseSchedulingPhase: true,
          runTasks: testCase.tasks,
        });
        const { totalCosts: total, masterCosts: master, workersCosts: workers } = testCase;
        expect(result).toEqual({ master, workers, total });
      });
    }
  });

  it('should return 0 when run is undefined', () => {
    const result: RunPriceEstimationResult = evaluateRunPrice(undefined);
    expect(result).toEqual({ master: 0, workers: 0, total: 0 });
  });
});
