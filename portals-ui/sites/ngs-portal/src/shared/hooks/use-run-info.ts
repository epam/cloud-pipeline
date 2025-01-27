import { fetchRun } from '@cloud-pipeline/api';
import type { Run } from '@cloud-pipeline/core';
import { useState, useEffect, useCallback, useMemo } from 'react';

export type RunInfoState = {
  pending: boolean;
  error: string | undefined;
  run?: Run;
};

export const useRunInfo = (
  runId: string | number | undefined,
): RunInfoState & { refresh: () => Promise<void> } => {
  const [state, setState] = useState<RunInfoState>({
    pending: true,
    error: undefined,
    run: undefined,
  });
  const refresh = useCallback(async () => {
    try {
      setState((curr) => ({
        ...curr,
        pending: true,
        error: undefined,
      }));
      const run = await fetchRun(Number(runId));
      setState({
        pending: false,
        error: undefined,
        run,
      });
    } catch (err) {
      const errorText =
        err instanceof Error
          ? err.message
          : `Failed to load run ${runId} info.`;
      setState({
        pending: false,
        error: errorText,
        run: undefined,
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
