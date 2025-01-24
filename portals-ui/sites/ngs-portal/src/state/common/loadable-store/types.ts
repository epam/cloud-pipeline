import type { AsyncState } from '../async-state/types.ts';

export type LoadableStoreState<StoreData> = AsyncState<StoreData> & {
  loaded: boolean;
};
export type LoadableStoreActions<StoreData> = {
  load: (force?: boolean) => Promise<StoreData>;
  refresh: () => Promise<StoreData>;
};

/**
 * `LoadableStore` is used for shared (cached) stores that are initialized with async loader (e.g., API call).
 */
export type LoadableStore<StoreData> = LoadableStoreState<StoreData> &
  LoadableStoreActions<StoreData>;
