import type { PipelinesState, PipelinesStore } from './types.ts';
import { useStore } from 'zustand';
import { pipelinesStore } from './store.ts';
import { useMemo } from 'react';

function usePipelinesStore(): PipelinesStore {
  return useStore(pipelinesStore);
}

export function usePipelinesState(): PipelinesState {
  const { pipelines, pending, error } = usePipelinesStore();
  return useMemo(
    () => ({
      pipelines,
      pending,
      error,
    }),
    [pipelines, pending, error],
  );
}
