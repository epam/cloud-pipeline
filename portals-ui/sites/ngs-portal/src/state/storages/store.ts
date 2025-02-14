import type { DataStoragesStore } from './types.ts';
import createLoadableStore from '../common/loadable-store/create-loadable-store.ts';
import { fetchAvailableDataStorages } from '@cloud-pipeline/api';

const dataStoragesStore = createLoadableStore<DataStoragesStore>(fetchAvailableDataStorages, [], () => ({}));

export { dataStoragesStore };
