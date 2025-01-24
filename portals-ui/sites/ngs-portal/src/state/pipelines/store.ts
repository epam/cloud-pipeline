import type { PipelinesStore } from './types.ts';
import createLoadableStore from '../common/loadable-store/create-loadable-store.ts';
import { fetchPipelines } from '@cloud-pipeline/api';

const pipelinesStore = createLoadableStore<PipelinesStore>(
  (abortSignal) => fetchPipelines({ abortSignal }),
  [],
  () => ({}),
);

export { pipelinesStore };
