import type { UserInfo } from '@cloud-pipeline/core';
import { asyncFilter } from '@cloud-pipeline/core';
import type {
  NgsItem,
  NgsItemsTagFilters,
  NgsItemsTagFilterConfiguration,
  NgsItemsSearchCallback,
} from '../types.ts';
import type { NgsTaggedObjectSettings } from '../../../shared/settings/types.ts';
import { NGS_ITEMS_OWNER_FILTER } from '../types.ts';
import { flattenStringIdentifiers } from '../../../shared/helpers';
import { stringArraysAreEqual } from '../../../shared/helpers/string.ts';

export function defaultSearchCallback<T extends NgsItem>(
  item: T,
  search?: string,
): boolean {
  return (
    !search ||
    search.trim().length === 0 ||
    item.name.trim().toLowerCase().includes(search.toLowerCase())
  );
}

export function ngsItemsTagFiltersEqual(
  a: NgsItemsTagFilters | undefined,
  b: NgsItemsTagFilters | undefined,
): boolean {
  const filtersA = a ?? {};
  const filtersB = b ?? {};
  const keysA = Object.keys(filtersA).sort();
  const keysB = Object.keys(filtersB).sort();
  if (!stringArraysAreEqual(keysA, keysB)) {
    return false;
  }
  for (let i = 0; i < keysA.length; i += 1) {
    if (keysA[i] !== keysB[i]) {
      return false;
    }
    const valuesA = filtersA[keysA[i]] ?? [];
    const valuesB = filtersB[keysB[i]] ?? [];
    if (!stringArraysAreEqual(valuesA, valuesB)) {
      return false;
    }
  }
  return true;
}

function filtersCallback<T extends NgsItem>(
  item: T,
  filters?: NgsItemsTagFilters,
): boolean {
  const { data = {}, owner } = item;
  for (const [key, values] of Object.entries(filters ?? {})) {
    if (values.length > 0) {
      const aValue =
        key === NGS_ITEMS_OWNER_FILTER
          ? owner
          : (key in data ? data[key] : undefined)?.value;
      if (aValue === undefined || !values.includes(aValue)) {
        return false;
      }
    }
  }
  return true;
}

export type FilterItemsOptions<T extends NgsItem> = {
  search?: string;
  searchCallback?: NgsItemsSearchCallback<T>;
  filters?: NgsItemsTagFilters;
};

export async function filterItems<T extends NgsItem>(
  items: T[],
  options?: FilterItemsOptions<T>,
): Promise<T[]> {
  const {
    search,
    searchCallback = defaultSearchCallback,
    filters = {},
  } = options ?? {};
  return asyncFilter(
    items,
    (item) => searchCallback(item, search) && filtersCallback(item, filters),
    {
      batch: 1,
    },
  );
}

export function excludeFilter(
  filters: NgsItemsTagFilters | undefined,
  filter: string,
): NgsItemsTagFilters | undefined {
  const { [filter]: _, ...rest } = filters ?? {};
  return Object.keys(rest).length > 0 ? rest : undefined;
}

type BuildNgsItemsTagFiltersConfigurationOptions = {
  taggedObjectSettings?: NgsTaggedObjectSettings;
  users?: UserInfo[];
  skipUsers?: boolean;
};

/**
 * Builds tag filters configuration based on items. A tag filters configuration is an array of
 * <tag - unique values> objects
 * @param items
 * @param options
 */
export function buildNgsItemsTagFiltersConfiguration(
  items: NgsItem[],
  options?: BuildNgsItemsTagFiltersConfigurationOptions,
): NgsItemsTagFilterConfiguration[] {
  const {
    taggedObjectSettings = {},
    users = [],
    skipUsers = false,
  } = options ?? {};
  const result: NgsItemsTagFilterConfiguration[] = [];
  // --- preparation
  const itemsByUsers = new Map<string, number>();
  const uniqueFilters = new Map<string, Map<string, number>>();
  const tagNames = new Map<string, string>();
  for (const item of items) {
    if (!skipUsers) {
      const value = itemsByUsers.get(item.owner) ?? 0;
      itemsByUsers.set(item.owner, value + 1);
    }
    for (const key of Object.keys(item.data ?? {})) {
      tagNames.set(key.toLowerCase(), key);
      const value = (item.data && key in item.data ? item.data[key] : undefined)
        ?.value;
      if (value !== undefined) {
        const filterValuesSet =
          uniqueFilters.get(key.toLowerCase()) ?? new Map<string, number>();
        const filterValueCount = filterValuesSet.get(value) ?? 0;
        filterValuesSet.set(value, filterValueCount + 1);
        uniqueFilters.set(key.toLowerCase(), filterValuesSet);
      }
    }
  }
  // --- build owners filter ---
  if (!skipUsers) {
    if (users.length > 0) {
      result.push({
        key: NGS_ITEMS_OWNER_FILTER,
        title: 'Owner',
        values: users.map((user) => ({
          value: user.name,
          display: user.name,
          count: itemsByUsers.get(user.name) ?? 0,
          data: user,
        })),
      });
    } else {
      result.push({
        key: NGS_ITEMS_OWNER_FILTER,
        title: 'Owner',
        values: [...itemsByUsers.entries()].map(([userName, count]) => ({
          value: userName,
          display: userName,
          count,
          data: userName,
        })),
      });
    }
  }
  // ---
  // building tags filters
  const tags = (() => {
    const { filterTags, tagsToDisplay, tagsToHide } = taggedObjectSettings;
    const keys = [...uniqueFilters.keys()];
    if (filterTags !== undefined) {
      return flattenStringIdentifiers(filterTags);
    }
    if (tagsToDisplay !== undefined) {
      return flattenStringIdentifiers(tagsToDisplay);
    }
    if (tagsToHide !== undefined) {
      const hide = flattenStringIdentifiers(tagsToHide).map((key) =>
        key.toLowerCase(),
      );
      return keys.filter((key) => !hide.includes(key.toLowerCase()));
    }
    return [];
  })();
  for (const tag of tags) {
    if (tag.toLowerCase() === NGS_ITEMS_OWNER_FILTER.toLowerCase()) {
      continue;
    }
    const config =
      uniqueFilters.get(tag.toLowerCase()) ?? new Map<string, number>();
    result.push({
      key: tag,
      title: tagNames.get(tag.toLowerCase()) ?? tag,
      values: [...config.entries()].map(([value, count]) => ({
        value,
        count,
        data: value,
      })),
    });
  }
  return result;
}
