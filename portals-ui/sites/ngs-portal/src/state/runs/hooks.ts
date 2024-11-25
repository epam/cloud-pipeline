import { useMemo } from 'react';
import type { RunsState, RunsStore } from './types.ts';
import { useStore } from 'zustand';
import { runsStore } from './store.ts';

function useRunsStore(): RunsStore {
  return useStore(runsStore);
}

export function useRunsState(): RunsState {
  const { runs, pending, error } = useRunsStore();
  return useMemo(
    () => ({
      runs,
      pending,
      error,
    }),
    [runs, pending, error],
  );
}
