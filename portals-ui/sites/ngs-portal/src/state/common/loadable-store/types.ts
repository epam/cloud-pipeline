export type LoadableStoreState<StoreData> = {
  pending: boolean;
  error: string | undefined;
  data: StoreData;
  loaded: boolean;
};
export type LoadableStoreActions<StoreData> = {
  load: (force?: boolean) => Promise<StoreData>;
  reload: () => Promise<StoreData>;
};

/**
 * `LoadableStore` is used for shared (cached) stores that are initialized with async loader (e.g., API call).
 */
export type LoadableStore<StoreData> = LoadableStoreState<StoreData> &
  LoadableStoreActions<StoreData>;
