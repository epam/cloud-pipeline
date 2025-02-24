import { createContext, useContext } from 'react';
import type { DataStorage, DataStorageItem, DataStorageItemTypes } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import type { StorageContents } from '../utils/storage-contents.ts';
import type { UIStorageItem } from '../types.ts';

export type StorageContext = {
  storage: DataStorage | undefined;
  path: string | undefined;
  onChangePath: (path: string | undefined) => void;
  pending: boolean;
  error: string | undefined;
  contents: StorageContents | undefined;
  selectionEnabled: boolean;
  selectedItems: DataStorageItem[];
  onSelectionChanged: (selectedItems: DataStorageItem[]) => void;
  onCreateItem: (type: DataStorageItemTypes) => void;
  onItemClick: (item: UIStorageItem) => void;
  onEditItem: (item: DataStorageItem) => void;
  onDeleteItem: (item: DataStorageItem) => void;
  onDownloadItem: (item: DataStorageItem) => void;
  loadNextPage: () => void;
  reloadPage: () => void;
};

export const storageContext = createContext<StorageContext>({
  storage: undefined,
  path: undefined,
  onChangePath: noop,
  pending: false,
  error: undefined,
  contents: undefined,
  selectionEnabled: false,
  selectedItems: [],
  onSelectionChanged: noop,
  onCreateItem: noop,
  onEditItem: noop,
  onDeleteItem: noop,
  onDownloadItem: noop,
  onItemClick: noop,
  reloadPage: noop,
  loadNextPage: noop,
});

export function useStorageContext(): StorageContext {
  return useContext(storageContext);
}
