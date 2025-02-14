import { DataStorage } from '../../model';
import { escapeRegExp } from '../misc';
import {
  FindDataStorageCriteria,
  FindDataStorageOptions,
  FindDataStorageScope,
  FindSingleDataStorageCriteria,
} from './types';

export function isFindDataStorageOptions(opts: any): opts is FindDataStorageOptions {
  return (
    opts !== undefined &&
    opts !== null &&
    typeof opts === 'object' &&
    'criteria' in opts &&
    (typeof opts.criteria === 'string' || typeof opts.criteria === 'number' || typeof opts.criteria === 'object')
  );
}

/**
 * Check if a user-specified scope matches the test one
 * @param scope - scope to check
 * @param matchesScope - scope to match, one of `FindDataStorageScope`
 */
export function matchesFindDataStorageScope(scope: number | undefined, matchesScope: FindDataStorageScope): boolean {
  const scopeToCheck = scope ?? 0;
  if (scope === 0) {
    return true;
  }
  return (scopeToCheck & matchesScope) == matchesScope;
}

/**
 * Casts storage search criteria or options to `FindDataStorageOptions` configuration
 * @param criteria
 */
export function castToFindDataStorageOptions(
  criteria: FindDataStorageCriteria | FindDataStorageOptions,
): FindDataStorageOptions {
  if (isFindDataStorageOptions(criteria)) {
    return criteria;
  }
  return {
    criteria,
    scope: FindDataStorageScope.all,
  };
}

/**
 * Searches storage by criteria
 * @param storages - list of storages to search in
 * @param criteria - if of `FindDataStorageCriteria` type, the exact search is will be used for `FindDataStorageScope.name` scopes
 */
export function findDataStorage(
  storages: DataStorage[],
  criteria: FindDataStorageCriteria | FindDataStorageOptions | undefined,
): DataStorage | undefined {
  if (!criteria) {
    return undefined;
  }
  const opts = castToFindDataStorageOptions(criteria);
  opts.exact = true;
  return findDataStorages(storages, opts)[0];
}

/**
 * Extracts storage identifiers and storage search strings from criteria
 * @param criteria
 */
export function parseFindSingleDataStorageCriteria(criteria: FindSingleDataStorageCriteria): {
  storageIds: number[];
  storageNames: string[];
  storageSearches: string[];
} {
  let storageIds: number[] = [];
  let storageNames: string[] = [];
  let storageSearches: string[] = [];
  if (typeof criteria === 'number') {
    storageIds = [criteria];
  } else if (typeof criteria === 'string') {
    const parts = criteria
      .split(/[,;\s]/)
      .map((part) => part.trim())
      .filter((part) => part.length > 0);
    storageIds = parts.map((part) => Number(part)).filter((part) => !Number.isNaN(part));
    storageSearches = parts.concat(criteria);
  } else {
    const { id, name, pathMask } = criteria;
    if (id) {
      storageIds = [id];
    }
    if (pathMask) {
      storageSearches = [pathMask];
    }
    if (name) {
      storageNames = [name];
    }
  }
  return {
    storageIds,
    storageNames,
    storageSearches,
  };
}

/**
 * Extracts storage identifiers and storage search strings from criteria
 * @param criteria
 */
export function parseFindDataStorageCriteria(criteria: FindDataStorageCriteria): {
  storageIds: number[];
  storageNames: string[];
  storageSearches: string[];
} {
  if (typeof criteria === 'object' && Array.isArray(criteria)) {
    const {
      storageIds: ids,
      storageNames: names,
      storageSearches: searches,
    } = criteria.map(parseFindSingleDataStorageCriteria).reduce<ReturnType<typeof parseFindSingleDataStorageCriteria>>(
      (res, cur) => ({
        storageIds: res.storageIds.concat(cur.storageIds),
        storageNames: res.storageNames.concat(cur.storageNames),
        storageSearches: res.storageSearches.concat(cur.storageSearches),
      }),
      {
        storageIds: [],
        storageNames: [],
        storageSearches: [],
      },
    );
    const storageIds = Array.from(new Set(ids));
    const storageNames = Array.from(new Set(names.map((ss) => ss.toLowerCase())));
    const storageSearches = Array.from(new Set(searches.map((ss) => ss.toLowerCase())));
    return {
      storageIds,
      storageNames,
      storageSearches,
    };
  }
  return parseFindSingleDataStorageCriteria(criteria);
}

/**
 * Search storages by criteria
 * @param storages - list of storages to search in
 * @param criteria - string (storage name or storage path mask), or number (storage id), or `FindDataStorageOptions`. If string or number provided, *partial* search will be performed
 */
export function findDataStorages(
  storages: DataStorage[],
  criteria: FindDataStorageCriteria | FindDataStorageOptions | undefined,
): DataStorage[] {
  if (!criteria) {
    return storages;
  }
  const { criteria: storage, exact = false, scope } = castToFindDataStorageOptions(criteria);
  if (storages.length === 0) {
    return [];
  }
  const { storageIds, storageNames, storageSearches } = parseFindDataStorageCriteria(storage);
  const ids = new Set(storageIds);
  let storageNameSearch: RegExp | undefined;
  if (storageNames.length > 0 || storageSearches.length > 0) {
    const regExp = [...new Set([...storageNames, ...storageSearches])].map((ss) => escapeRegExp(ss)).join('|');
    if (exact) {
      storageNameSearch = new RegExp(`^(${regExp})$`, 'i');
    } else {
      storageNameSearch = new RegExp(`(${regExp})`, 'i');
    }
  }
  return storages.filter((st) => {
    const checkScope = (aScopeToCheck: FindDataStorageScope): boolean => {
      if (matchesFindDataStorageScope(scope, aScopeToCheck)) {
        switch (aScopeToCheck) {
          case FindDataStorageScope.path:
            // for `path` scope, we should check if storage pathMask equals *exactly* provided search criteria,
            // or search criteria is a "nested" path of storage pathMask, i.e.:
            // - search criteria: "s3://some-storage-path-mask/folder/sub-folder/file"
            // - matched storage path masks: "s3://some-storage-path-mask", "s3://some-storage-path-mask/folder", "s3://some-storage-path-mask/folder/sub-folder"
            let { pathMask } = st;
            if (!pathMask || pathMask.length === 0) {
              return false;
            }
            pathMask = pathMask.toLowerCase();
            return storageSearches.some(
              (s) => s.toLowerCase() === pathMask || s.toLowerCase().startsWith(`${pathMask}/`),
            );
          case FindDataStorageScope.name:
          default:
            return storageNameSearch ? storageNameSearch.test(st.name) : false;
        }
      }
      return false;
    };
    const checkId = () => {
      return ids.size === 0 ? false : ids.has(st.id);
    };
    return checkId() || checkScope(FindDataStorageScope.name) || checkScope(FindDataStorageScope.path);
  });
}
