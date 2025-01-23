import { useMemo } from 'react';
import { fetchRunLogs } from '@cloud-pipeline/api';
import { useLoadableStateWithInterval } from './use-loadable-state';
import { type RunLog } from '@cloud-pipeline/core';

const INTERVAL_MS = 5000;

export type RunsLogsResult = {
  logs?: RunLog[];
  pending: boolean;
  error: string | undefined;
};

type Parameters = {
  task?: string;
  interval?: number;
};

export function useRunLogs(
  runId: number,
  parameters?: Parameters,
): RunsLogsResult {
  const { task, interval = INTERVAL_MS } = parameters ?? {};
  const {
    pending,
    error,
    state: logs,
  } = useLoadableStateWithInterval(interval, fetchRunLogs, runId, task);
  return useMemo(
    () => ({
      logs,
      pending,
      error,
    }),
    [logs, pending, error],
  );
}
