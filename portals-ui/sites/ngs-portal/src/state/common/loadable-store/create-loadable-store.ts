import type { LoadableStore } from './types.ts';
import type { StoreApi } from 'zustand';
import { createStore } from 'zustand';

type StoreDataType<T> = T extends LoadableStore<infer U> ? U : never;
type RestStoreType<T> =
  T extends LoadableStore<infer U> ? Omit<T, keyof LoadableStore<U>> : T;

function createLoadableStore<
  StoreData,
>(
  loader: (
    abortSignal: AbortSignal,
    setter: StoreApi<LoadableStore<StoreData | undefined>>['setState'],
    getter: StoreApi<LoadableStore<StoreData | undefined>>['getState'],
  ) => Promise<StoreData | undefined>,
): StoreApi<LoadableStore<StoreData | undefined>>;
function createLoadableStore<
  Store extends LoadableStore<StoreData>,
  StoreData = StoreDataType<Store>,
>(
  loader: (
    abortSignal: AbortSignal,
    setter: StoreApi<Store>['setState'],
    getter: StoreApi<Store>['getState'],
  ) => Promise<StoreData>,
  defaultValue: StoreData,
  storeInitializer: (
    setter: StoreApi<Store>['setState'],
    getter: StoreApi<Store>['getState'],
  ) => RestStoreType<Store>,
): StoreApi<Store>;
function createLoadableStore<
  Store extends LoadableStore<StoreData>,
  StoreData = StoreDataType<Store>,
>(
  loader: (
    abortSignal: AbortSignal,
    setter: StoreApi<Store>['setState'],
    getter: StoreApi<Store>['getState'],
  ) => Promise<StoreData>,
  defaultValue?: StoreData,
  storeInitializer?: (
    setter: StoreApi<Store>['setState'],
    getter: StoreApi<Store>['getState'],
  ) => RestStoreType<Store>,
): StoreApi<Store> {
  let abortController: AbortController | undefined;
  let token: unknown;
  let currentPromise: Promise<StoreData> | undefined;
  return createStore<Store>(
    (set, get) =>
      ({
        ...(storeInitializer ? storeInitializer(set, get) : {}),
        pending: false,
        error: undefined,
        data: defaultValue,
        loaded: false,
        async load(force = false): Promise<StoreData> {
          if (!force && currentPromise) {
            return currentPromise;
          }
          currentPromise = (async () => {
            const requestToken = {};
            token = requestToken;
            if (abortController) {
              abortController.abort();
            }
            abortController = new AbortController();
            set({ pending: true, error: undefined } as Partial<Store>);
            try {
              const result = await loader(
                abortController.signal,
                set,
                get,
              );
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
                abortController = undefined;
              }
            }
          })();
          return currentPromise;
        },
        async reload(): Promise<StoreData> {
          const { load } = get();
          return load(true);
        },
      }) as Store,
  );
}

export default createLoadableStore;
