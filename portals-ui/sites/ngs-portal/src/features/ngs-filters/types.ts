import type { NgsData } from '@cloud-pipeline/core';

export type Tag = {
  id: string;
  count: number;
};

export type FilterToDisplay = { id: string; label: string };

export type TagFilters = Record<string, string[]>;

export type NgsTags = Record<string, { label: string; values: Tag[] }>;

export type NgsItem = {
  owner: string;
  name: string;
  data?: NgsData;
};
