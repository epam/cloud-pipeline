import type { Run } from '@cloud-pipeline/core';

export type RunsState = {
  runs: Run[] | undefined;
  error: string | undefined;
  pending: boolean;
};

export type RunsActions = {
  setError: (error: string | undefined) => void;
  setPending: (pending: boolean) => void;
  setRuns: (result: Pick<RunsState, 'runs' | 'error'>) => void;
};

export type RunsStore = RunsState & RunsActions;
