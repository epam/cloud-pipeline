import type { DataStorage } from '@cloud-pipeline/core';
import type { LoadableStoreActions, LoadableStoreState } from '../common/loadable-store/types.ts';

export type DataStoragesState = LoadableStoreState<DataStorage[]>;

export type DataStoragesActions = LoadableStoreActions<DataStorage[]>;

export type DataStoragesStore = DataStoragesState & DataStoragesActions;
