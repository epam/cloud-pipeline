import type {
  NgsItem,
  NgsItemsFiltersOptions,
  NgsItemsFiltersState,
  NgsItemsSearchCallback,
  NgsItemsTagFilterConfiguration,
} from '../types.ts';
import {
  buildNgsItemsTagFiltersConfiguration,
  defaultSearchCallback,
  filterItems,
  ngsItemsTagFiltersEqual,
} from './filter-items.ts';
import type { NgsTaggedObjectSettings } from '../../../shared/settings/types.ts';
import type { UserInfo } from '@cloud-pipeline/core';

export type NgsItemsFiltersProcessorState<T extends NgsItem> =
  NgsItemsFiltersState & {
    pending: boolean;
    error: string | undefined;
    filteredItems: T[];
    config: NgsItemsTagFilterConfiguration[];
  };

type NgsItemsFiltersProcessorListener<T extends NgsItem> = (
  state: NgsItemsFiltersProcessorState<T>,
) => void;

export type NgsItemsFiltersProcessorOptions<T extends NgsItem> =
  Partial<NgsItemsFiltersState> &
    Partial<NgsItemsFiltersOptions<T>> & {
      items?: T[];
      listener?: NgsItemsFiltersProcessorListener<T>;
      users?: UserInfo[];
    };

class AbortError extends Error {}

/**
 * A helper class for filtering items (`NgsItem`) based on the search criteria and tag filters.
 * This class supports asynchronous filtering, filter configuration generation
 * and minimizes the filter operations by caching some of them.
 *
 * Components should register themselves using instance's `addListener` method.
 */
class NgsItemsFiltersProcessor<T extends NgsItem> {
  private _listeners: NgsItemsFiltersProcessorListener<T>[];
  private _pending: boolean;
  private _configPending: boolean;
  private _error: string | undefined;
  private _configError: string | undefined;
  private _items: T[];
  private _filteredItems: T[];
  private readonly _state: NgsItemsFiltersState;
  private _abortController: AbortController;
  private _configAbortController: AbortController;
  private _filterToken: unknown;
  private _configToken: unknown;
  private _searchCallback: NgsItemsSearchCallback<T> | undefined;
  private _filtersBaseConfig: NgsItemsTagFilterConfiguration[];
  private _filtersConfig: NgsItemsTagFilterConfiguration[];
  private _configBuildPromise: Promise<void> | undefined;
  private _users: UserInfo[];
  private _taggedObjectSettings: NgsTaggedObjectSettings | undefined;
  private readonly _filtersEnabled: boolean;

  constructor(options: NgsItemsFiltersProcessorOptions<T>) {
    const {
      items = [],
      listener,
      searchCallback = defaultSearchCallback,
      search,
      filters,
      taggedObjectSettings,
      users = [],
      filtersEnabled = true,
    } = options;
    this._abortController = new AbortController();
    this._configAbortController = new AbortController();
    this._filterToken = {};
    this._configToken = {};
    this._items = items.slice();
    this._filteredItems = items.slice();
    this._searchCallback = searchCallback;
    this._listeners = [];
    this._pending = false;
    this._configPending = false;
    this._error = undefined;
    this._configError = undefined;
    this._state = {
      search,
      filters,
    };
    this._users = users.slice();
    this._taggedObjectSettings = taggedObjectSettings;
    this._filtersBaseConfig = [];
    this._filtersConfig = [];
    this._configBuildPromise = undefined;
    this._filtersEnabled = filtersEnabled;
    if (listener) {
      this.addListener(listener);
    }
    this.perform();
  }

  setUsers(users: UserInfo[], perform = true): boolean {
    this._users = users.slice();
    this.abortConfig();
    if (perform) {
      this.perform();
    }
    return true;
  }

  setItems(items: T[], perform = true): boolean {
    this._items = items;
    this.abortConfig();
    if (perform) {
      this.perform();
    }
    return true;
  }

  setFiltersState(state: NgsItemsFiltersState, perform = true): boolean {
    const { search = '', filters } = state;
    const { search: currentSearch = '', filters: currentFilters } = this._state;
    if (
      search.trim() !== currentSearch.trim() ||
      !ngsItemsTagFiltersEqual(filters, currentFilters)
    ) {
      this._state.search = search;
      this._state.filters = filters;
      if (perform) {
        this.perform();
      }
      return true;
    }
    return false;
  }

  setPayload(payload: NgsItemsFiltersState & { items: T[]; users: UserInfo[] }) {
    const usersSet = this.setUsers(payload.users);
    const itemsSet = this.setItems(payload.items);
    const filtersSet = this.setFiltersState(payload);
    if (itemsSet || filtersSet || usersSet) {
      this.perform();
    }
  }

  destroy() {
    this.abort();
    this.abortConfig();
    this._items = [];
    this._filteredItems = [];
    this._listeners = [];
    this._users = [];
    this._taggedObjectSettings = undefined;
    this._configBuildPromise = undefined;
    this._searchCallback = undefined;
  }

  addListener(listener: NgsItemsFiltersProcessorListener<T>) {
    this.removeListener(listener);
    this._listeners.push(listener);
  }

  removeListener(listener: NgsItemsFiltersProcessorListener<T>) {
    this._listeners = this._listeners.filter((l) => l !== listener);
  }

  getState(): NgsItemsFiltersProcessorState<T> {
    return {
      ...this._state,
      pending: this._pending || this._configPending,
      error: this._error || this._configError,
      filteredItems: this._filteredItems,
      config: this._filtersConfig,
    };
  }

  protected abort() {
    this._filterToken = {};
    this._abortController.abort();
  }

  protected abortConfig() {
    this._configToken = {};
    this._configAbortController.abort();
    this._configBuildPromise = undefined;
  }

  /**
   * Builds filters configuration (cacheable)
   * @protected
   */
  protected async buildConfig() {
    if (!this._configBuildPromise) {
      this._configBuildPromise = (async () => {
        this.abortConfig();
        const abortController = (this._configAbortController =
          new AbortController());
        const token = (this._configToken = {});
        const commit = (fn: () => void) => {
          if (token === this._configToken && !abortController.signal.aborted) {
            fn();
            this.reportState();
          }
        };
        commit(() => {
          this._configPending = true;
          this._configError = undefined;
        });
        try {
          if (this._filtersEnabled) {
            const config = buildNgsItemsTagFiltersConfiguration(this._items, {
              taggedObjectSettings: this._taggedObjectSettings,
              users: this._users,
            });
            commit(() => {
              this._configPending = false;
              this._filtersBaseConfig = config;
            });
          } else {
            commit(() => {
              this._configPending = false;
            });
          }
        } catch (error) {
          commit(() => {
            this._configPending = false;
            if (!(error instanceof AbortError)) {
              this._configError =
                error instanceof Error ? error.message : `${error}`;
            }
          });
        }
      })();
    }
    return this._configBuildPromise;
  }

  /**
   * Performs items filtering
   * @protected
   */
  protected perform() {
    this.abort();
    const abortController = (this._abortController = new AbortController());
    const token = (this._filterToken = {});
    const commit = (fn: () => void) => {
      if (token === this._filterToken && !abortController.signal.aborted) {
        fn();
        this.reportState();
      }
    };
    commit(() => {
      this._pending = true;
      this._error = undefined;
    });
    (async () => {
      try {
        if (abortController.signal.aborted) {
          throw new AbortError('filter items aborted');
        }
        const [filtered] = await Promise.all([
          filterItems(this._items, {
            ...this._state,
            searchCallback: this._searchCallback,
          }),
          this.buildConfig(),
        ]);
        if (abortController.signal.aborted) {
          throw new AbortError('filter items aborted');
        }
        commit(() => {
          this._filteredItems = filtered;
        });
        if (this._filtersEnabled) {
          const { filters = {}, search } = this._state;
          // `config` is a copy of filters configuration for unfiltered items list
          const config = this._filtersBaseConfig.map((c) => ({
            ...c,
            values: c.values.map((v) => ({ ...v })),
          }));
          const baseConfig = buildNgsItemsTagFiltersConfiguration(filtered, {
            taggedObjectSettings: {
              filterTags: this._filtersBaseConfig.map((cfg) => cfg.key),
            },
          });
          for (const cfg of config) {
            const details = baseConfig.find((c) => c.key === cfg.key);
            if (details) {
              const map = details.values.reduce<Record<string, number>>(
                (r, c) => ({
                  ...r,
                  [c.value]: c.count,
                }),
                {},
              );
              for (const entry of cfg.values) {
                entry.count =
                  (entry.value in map ? map[entry.value] : undefined) ?? 0;
              }
            }
          }
          const cache = new Map<string, T[]>();
          const keys = Object.keys(filters);
          const buildConfigForKey = async (keyIdx = 0) => {
            if (keyIdx >= keys.length || abortController.signal.aborted) {
              return;
            }
            const key = keys[keyIdx];
            const { [key]: _, ...rest } = filters;
            const restKeys = JSON.stringify(Object.keys(rest).sort());
            if (!cache.has(restKeys)) {
              const sub = await filterItems(this._items, {
                searchCallback: this._searchCallback,
                filters: Object.keys(rest).length === 0 ? undefined : rest,
                search,
              });
              cache.set(restKeys, sub);
            }
            const sub = cache.get(restKeys) ?? [];
            const subConfig = buildNgsItemsTagFiltersConfiguration(sub, {
              taggedObjectSettings: { filterTags: [key] },
            }).find((c) => c.key === key);
            const mainConfig = config.find((c) => c.key === key);
            if (subConfig && mainConfig) {
              mainConfig.values.forEach((value) => {
                value.count = 0;
              });
              for (const entry of subConfig.values) {
                const mainEntry = mainConfig.values.find(
                  (v) => v.value === entry.value,
                );
                if (mainEntry) {
                  mainEntry.count = entry.count;
                } else {
                  mainConfig.values.push(entry);
                }
              }
            }
            await buildConfigForKey(keyIdx + 1);
          };
          await buildConfigForKey();
          cache.clear();
          commit(() => {
            this._filtersConfig = config;
          });
        }
        commit(() => {
          this._pending = false;
          this._error = undefined;
        });
      } catch (error) {
        if (!(error instanceof AbortError)) {
          console.warn('error filtering items:');
          console.warn(error);
        }
        commit(() => {
          this._pending = false;
          if (!(error instanceof AbortError)) {
            this._error = error instanceof Error ? error.message : `${error}`;
          }
        });
      }
    })();
  }

  protected reportState() {
    const state = this.getState();
    for (const listener of this._listeners) {
      listener(state);
    }
  }
}

export { NgsItemsFiltersProcessor };
