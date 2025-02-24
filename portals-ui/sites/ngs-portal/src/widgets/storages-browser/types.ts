import type { DataStorage, DataStorageItem } from '@cloud-pipeline/core';

export type DataStorageItemExtended = DataStorageItem & {
  storage: DataStorage;
};
