import type { NavigateBack, UIStorageItem } from '../types.ts';
import type { DataStorageItem } from '@cloud-pipeline/core';

export const ROOT_PLACEHOLDER = '/';

export function isDataStorageItem(item: UIStorageItem): item is DataStorageItem {
  return item.type !== 'navigateBack';
}

export function isNavigateBackItem(item: UIStorageItem): item is NavigateBack {
  return item.type === 'navigateBack';
}

export function correctStoragePath(path: string | undefined): string {
  return !path || path.trim() === '' ? ROOT_PLACEHOLDER : path;
}
