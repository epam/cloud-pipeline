import { fetchRunTasks } from '@cloud-pipeline/api';
import type { RunTask } from '@cloud-pipeline/core';
import { useState, useEffect, useCallback, useMemo } from 'react';

export type RunTasksState = {
  pending: boolean;
  error: string | undefined;
  tasks?: RunTask[];
};

export const useRunTasks = (
  runId: string | number | undefined,
): RunTasksState & { refresh: () => Promise<void> } => {
  const [state, setState] = useState<RunTasksState>({
    pending: true,
    error: undefined,
    tasks: undefined,
  });
  const refresh = useCallback(async () => {
    try {
      setState((curr) => ({
        ...curr,
        pending: true,
        error: undefined,
      }));
      const tasks = await fetchRunTasks(Number(runId));
      setState({
        pending: false,
        error: undefined,
        tasks,
      });
    } catch (err) {
      const errorText =
        err instanceof Error
          ? err.message
          : `Failed to load run ${runId} tasks.`;
      setState({
        pending: false,
        error: errorText,
        tasks: undefined,
      });
    }
  }, [runId]);
  useEffect(() => {
    if (runId !== undefined) {
      void refresh();
    }
  }, [refresh, runId]);
  return useMemo(
    () => ({
      ...state,
      refresh,
    }),
    [refresh, state],
  );
};
