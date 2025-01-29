import type { PipelinesStore } from './types.ts';
import createLoadableStore from '../common/loadable-store/create-loadable-store.ts';
import { fetchNgsPipelines } from './fetch-ngs-pipelines.ts';

const pipelinesStore = createLoadableStore<PipelinesStore>(
  fetchNgsPipelines,
  [],
  () => ({}),
);

export { pipelinesStore };
