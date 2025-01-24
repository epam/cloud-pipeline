import type { LoadableStore } from './types.ts';
import type { StoreApi } from 'zustand';
import { createStore } from 'zustand';

type StoreDataType<T> = T extends LoadableStore<infer U> ? U : never;
type RestStoreType<T> =
  T extends LoadableStore<infer U> ? Omit<T, keyof LoadableStore<U>> : T;

export default function createLoadableStore<
  Store extends LoadableStore<StoreData>,
  StoreData = StoreDataType<Store>,
>(
  loader: (abortSignal?: AbortSignal) => Promise<StoreData>,
  defaultValue: StoreData,
  storeInitializer: (
    setter: StoreApi<Store>['setState'],
    getter: StoreApi<Store>['getState'],
  ) => RestStoreType<Store>,
): StoreApi<Store> {
  let abortController: AbortController | undefined;
  let token: unknown;
  let inProgress = false;
  return createStore<Store>(
    (set, get) =>
      ({
        ...storeInitializer(set, get),
        pending: false,
        error: undefined,
        data: defaultValue,
        loaded: false,
        async load(force = false): Promise<StoreData> {
          const { loaded, data } = get();
          if ((!loaded && !inProgress) || force) {
            inProgress = true;
            const requestToken = {};
            token = requestToken;
            if (abortController) {
              abortController.abort();
            }
            abortController = new AbortController();
            set({ pending: true, error: undefined } as Partial<Store>);
            try {
              const result = await loader(abortController.signal);
              if (requestToken === token) {
                set({
                  pending: false,
                  error: undefined,
                  loaded: true,
                  data: result,
                } as Partial<Store>);
              }
              return result;
            } catch (error) {
              if (requestToken === token) {
                set({
                  pending: false,
                  error:
                    error instanceof Error
                      ? error.message
                      : 'error fetching data',
                  loaded: false,
                } as Partial<Store>);
              }
              throw error;
            } finally {
              if (requestToken === token) {
                inProgress = false;
                abortController = undefined;
              }
            }
          }
          return data;
        },
        async reload(): Promise<StoreData> {
          const { load } = get();
          return load(true);
        },
      }) as Store,
  );
}
