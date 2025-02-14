import { DataStorage } from '../../model';

export type DataStorageSearchParams = Pick<DataStorage, 'id' | 'name' | 'pathMask'>;

/**
 * Single storage search criteria. Possible types:
 * - type number: a storage identifier
 * - type string: a storage name or pathMask, or comma/space/semicolon separated string of storage identifiers or names or pathMasks
 * - type `Partial<DataStorageSearchParams>`: storage identifier, name or pathMask (if provided)
 */
export type FindSingleDataStorageCriteria = string | number | Partial<DataStorageSearchParams>;
export type FindDataStorageCriteria = FindSingleDataStorageCriteria | FindSingleDataStorageCriteria[];

export enum FindDataStorageScope {
  /**
   * Search storage by name
   */
  name = 1 << 0,
  /**
   * Search storage by path
   */
  path = 1 << 1,
  all = 0,
}

export type FindDataStorageOptions = {
  criteria: FindDataStorageCriteria;
  /**
   * Only affects `FindDataStorageScope.name` scope.
   * If `criteria` is a string, searches storage name by exact (`true`) or partial (`false`) match.
   *
   * Default value: false
   */
  exact?: boolean;
  /**
   * If `criteria` is a string, performs search on the specified scope(s), see `FindDataStorageScope`
   */
  scope?: number;
};
