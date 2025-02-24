import { createContext, useContext } from 'react';
import type { DataStorage, DataStorageItemTypes } from '@cloud-pipeline/core';
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
  loadNextPage: () => void;
  onItemClick: (item: UIStorageItem) => void;
  openCreateModal: (entityType: DataStorageItemTypes) => void;
  onRowEditClick: (entityType: DataStorageItemTypes, name: string) => void;
  onRowDeleteClick: (entityType: DataStorageItemTypes, entityName: string, path: string) => void;
  handleDownload: (name: string, path: string) => void;
  reloadPage: () => void;
};

export const storageContext = createContext<StorageContext>({
  storage: undefined,
  path: undefined,
  onChangePath: noop,
  pending: false,
  error: undefined,
  contents: undefined,
  openCreateModal: noop,
  onRowEditClick: noop,
  onRowDeleteClick: noop,
  handleDownload: noop,
  onItemClick: noop,
  reloadPage: noop,
  loadNextPage: noop,
});

export function useStorageContext(): StorageContext {
  return useContext(storageContext);
}
