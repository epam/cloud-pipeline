import type { NavigateBack, UIStorageItem } from '../types.ts';
import type { DataStorageItem } from '@cloud-pipeline/core';

export function isDataStorageItem(item: UIStorageItem): item is DataStorageItem {
  return item.type !== 'navigateBack';
}

export function isNavigateBackItem(item: UIStorageItem): item is NavigateBack {
  return item.type === 'navigateBack';
}
