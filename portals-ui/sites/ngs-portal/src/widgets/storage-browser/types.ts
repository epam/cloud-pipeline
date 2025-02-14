import type { DataStorageItem } from '@cloud-pipeline/core';

export type PageMarker = {
  currentPage: number;
  markers: Array<string | undefined>;
};

export type PageMarkers = Record<string, PageMarker>;

export type StoragePaging = {
  marker: string | undefined;
  currentPage: number;
  canNavigateNext: boolean;
  canNavigatePrev: boolean;
};

export type NavigateBack = Pick<DataStorageItem, 'name' | 'path'> & {
  type: 'navigateBack';
};

export type UIStorageItem = DataStorageItem | NavigateBack;
