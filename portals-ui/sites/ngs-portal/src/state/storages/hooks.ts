import type { DataStoragesStore } from './types.ts';
import { dataStoragesStore } from './store.ts';
import { useLoadableStore, useRefreshLoadableStore } from '../common/loadable-store/hooks.ts';
import type { DataStorage, FindDataStorageCriteria, FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { findDataStorage, findDataStorages } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import { useCallback, useEffect, useMemo } from 'react';

export function useDataStoragesStore(): DataStoragesStore {
  return useLoadableStore(dataStoragesStore);
}

export function useDataStorages(): DataStorage[] {
  return useDataStoragesStore().data;
}

export function useReloadDataStoragesFn(): () => Promise<DataStorage[]> {
  return useRefreshLoadableStore(dataStoragesStore);
}

export function useReloadDataStorage() {
  const fn = useReloadDataStoragesFn();
  useEffect(() => {
    fn().then(noop).catch(noop);
  }, [fn]);
}

export function useSearchDataStorages(
  storages?: DataStorage[],
): (storage: FindDataStorageCriteria | undefined) => DataStorage[] {
  const storeStorages = useDataStorages();
  return useCallback(
    (storage: FindDataStorageCriteria | undefined): DataStorage[] => {
      const storagesToSearch = storages ?? storeStorages;
      return findDataStorages(storagesToSearch, storage ? { criteria: storage, exact: false } : undefined);
    },
    [storages, storeStorages],
  );
}

export function useSearchDataStorage(
  storages?: DataStorage[],
): (storage: FindDataStorageCriteria | undefined) => DataStorage | undefined {
  const storeStorages = useDataStorages();
  return useCallback(
    (storage: FindDataStorageCriteria | undefined): DataStorage | undefined => {
      const storagesToSearch = storages ?? storeStorages;
      return findDataStorage(storagesToSearch, storage ? { criteria: storage, exact: true } : undefined);
    },
    [storages, storeStorages],
  );
}

export function useDataStorage(storage?: FindSingleDataStorageCriteria): DataStorage | undefined {
  const search = useSearchDataStorage();
  return useMemo(() => search(storage), [search, storage]);
}

export function useDataStoragesByCriteria(criteria?: FindDataStorageCriteria): DataStorage[] {
  const search = useSearchDataStorages();
  return useMemo(() => search(criteria), [search, criteria]);
}
