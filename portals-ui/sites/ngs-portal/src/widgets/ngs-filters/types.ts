import type { NgsData } from '@cloud-pipeline/core';
import type { NgsTaggedObjectSettings } from '../../shared/settings/types.ts';

export type Tag = {
  id: string;
  count: number;
};

export const NGS_ITEMS_OWNER_FILTER = 'owner';

export type NgsItemsTagFilterValue<T = unknown> = {
  value: string;
  count: number;
  data?: T;
  display?: string;
};

export type NgsItemsTagFilterConfiguration = {
  key: string;
  title: string;
  values: NgsItemsTagFilterValue[];
};

export type NgsItemsTagFilters = Record<string, string[]>;

export type NgsItem = {
  owner: string;
  name: string;
  data?: NgsData;
};

export type NgsItemsSearchCallback<T extends NgsItem> = (
  item: T,
  search?: string,
) => boolean;

export type NgsItemsFiltersOptions<T extends NgsItem> = {
  searchCallback?: NgsItemsSearchCallback<T>;
  taggedObjectSettings?: NgsTaggedObjectSettings;
  filtersEnabled?: boolean;
};

export type NgsItemsFiltersState = {
  search: string | undefined;
  filters: NgsItemsTagFilters | undefined;
};

export type NgsItemsFiltersActions = {
  onSearchChanged: (search: string | undefined) => void;
  onFiltersChanged: (filters: NgsItemsTagFilters | undefined) => void;
};

export type NgsFilterProps = {
  config: NgsItemsTagFilterConfiguration[];
  filters?: NgsItemsTagFilters;
  onFiltersChange?: (filters: NgsItemsTagFilters | undefined) => void;
};
