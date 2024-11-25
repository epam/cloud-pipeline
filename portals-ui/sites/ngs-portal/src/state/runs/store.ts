import { createStore } from 'zustand';
import type { RunsState, RunsStore } from './types.ts';

const runsStore = createStore<RunsStore>((set) => ({
  runs: undefined,
  error: undefined,
  pending: false,
  setRuns(result: Pick<RunsState, 'runs' | 'error'>) {
    const { runs, error } = result;
    set({ runs, error, pending: false });
  },
  setError(error: string | undefined) {
    set({ error });
  },
  setPending(pending: boolean) {
    set({ pending });
  },
}));

export { runsStore };
