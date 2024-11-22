import { createStore } from 'zustand';
import type { PipelinesState, PipelinesStore } from './types.ts';

const pipelinesStore = createStore<PipelinesStore>((set) => ({
  pipelines: undefined,
  error: undefined,
  pending: false,
  setPipelines(result: Pick<PipelinesState, 'pipelines' | 'error'>) {
    const { pipelines, error } = result;
    set({ pipelines, error, pending: false });
  },
  setError(error: string | undefined) {
    set({ error });
  },
  setPending(pending: boolean) {
    set({ pending });
  },
}));

export { pipelinesStore };
