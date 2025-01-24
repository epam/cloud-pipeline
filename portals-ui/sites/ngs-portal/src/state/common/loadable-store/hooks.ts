import { useStore } from 'zustand';
import { useEffect } from 'react';
import type { StoreApi } from 'zustand';
import type { LoadableStore } from './types.ts';

export function useLoadableStore<
  StoreData,
  Store extends LoadableStore<StoreData>,
>(loadableStoreApi: StoreApi<Store>): Store {
  useEffect(() => {
    void loadableStoreApi.getState().load();
  }, [loadableStoreApi]);
  return useStore(loadableStoreApi);
}

export function useRefreshLoadableStore<StoreData>(
  loadableStoreApi: StoreApi<LoadableStore<StoreData>>,
): () => Promise<StoreData> {
  return useStore(loadableStoreApi).refresh;
}
