import {createStore, StateCreator, StoreApi} from 'zustand';
import {
  createJSONStorage,
  persist,
  type PersistStorage,
  type StorageValue,
} from 'zustand/middleware';

function isPersistEnvelope(value: unknown): value is StorageValue<unknown> {
  return typeof value === 'object' && value !== null && 'state' in value;
}

function createLegacyAwareJSONStorage<S>(getStorage: () => Storage): PersistStorage<S> | undefined {
  const jsonStorage = createJSONStorage<S>(getStorage);
  if (!jsonStorage) {
    return undefined;
  }

  const normalize = (parsed: S | StorageValue<S> | null): StorageValue<S> | null => {
    if (parsed === null) {
      return null;
    }
    if (isPersistEnvelope(parsed)) {
      return parsed as StorageValue<S>;
    }
    return {state: parsed as S, version: 0};
  };

  return {
    getItem: (name) => {
      const value = jsonStorage.getItem(name);
      if (value instanceof Promise) {
        return value.then(normalize);
      }
      return normalize(value as S | StorageValue<S> | null);
    },
    setItem: jsonStorage.setItem,
    removeItem: jsonStorage.removeItem,
  };
}

export type LocalStorageStoreOptions<StoreState extends object> = {
  localStorageKey: string;
  defaultValue?: StoreState;
  keys?: keyof StoreState;
  mapper?: (o: unknown) => Partial<StoreState> | undefined;
};

function partializeState<StoreState extends object, StoreActions>(
  state: StoreState & StoreActions,
  defaultValue: StoreState | undefined,
  key: keyof StoreState | undefined,
): Partial<StoreState> {
  if (key !== undefined) {
    const result: Partial<StoreState> = {};
    result[key] = state[key];
    return result;
  }
  if (defaultValue) {
    const result = {} as Partial<StoreState>;
    for (const stateKey of Object.keys(defaultValue) as (keyof StoreState)[]) {
      result[stateKey] = state[stateKey];
    }
    return result;
  }
  const result = {} as Partial<StoreState>;
  for (const stateKey of Object.keys(state) as (keyof StoreState)[]) {
    const value = state[stateKey];
    if (typeof value !== 'function') {
      result[stateKey] = value;
    }
  }
  return result;
}

export function createLocalStorageStore<StoreState extends object, StoreActions>(
  init: (
    setter: StoreApi<StoreState & StoreActions>['setState'],
    getter: StoreApi<StoreState & StoreActions>['getState'],
  ) => StoreState & StoreActions,
  options: LocalStorageStoreOptions<StoreState>,
): StoreApi<StoreState & StoreActions> {
  const {defaultValue, localStorageKey: storageKey, keys, mapper} = options;
  type FullState = StoreState & StoreActions;

  const stateCreator: StateCreator<FullState, [['zustand/persist', Partial<StoreState>]]> = (
    set,
    get,
  ) => ({
    ...defaultValue,
    ...init(set, get),
  });

  return createStore<FullState>()(
    persist(stateCreator, {
      name: storageKey,
      storage: createLegacyAwareJSONStorage<Partial<StoreState>>(() => localStorage),
      partialize: (state) => partializeState(state, defaultValue, keys),
      merge: (persistedState, currentState) => {
        try {
          const mapped = mapper ? mapper(persistedState) : (persistedState as Partial<StoreState>);
          if (!mapped || typeof mapped !== 'object') {
            return currentState;
          }
          return {...currentState, ...mapped};
        } catch {
          return currentState;
        }
      },
    }),
  );
}
