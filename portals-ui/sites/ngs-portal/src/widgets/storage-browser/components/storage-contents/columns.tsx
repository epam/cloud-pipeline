import { DataStorageItemTypes, displayDate, displaySize } from '@cloud-pipeline/core';
import { DocumentIcon, FolderIcon } from '@heroicons/react/24/outline';
import type { ColumnType } from 'antd/es/table';
import type { UIStorageItem } from '../../types.ts';

export const storageItemTypeColumn: ColumnType<UIStorageItem> = {
  dataIndex: 'type',
  key: 'type',
  width: 25,
  render: (value: UIStorageItem['type']) => {
    if (value === DataStorageItemTypes.file) {
      return <DocumentIcon className="w-4 h-4" />;
    }
    if (value === DataStorageItemTypes.folder || value === 'navigateBack') {
      return <FolderIcon className="w-4 h-4" />;
    }
  },
};

export const storageItemNameColumn: ColumnType<UIStorageItem> = {
  title: <span className="text-xs">Name</span>,
  dataIndex: 'name',
  key: 'name',
  width: 200,
};

export const storageItemSizeColumn: ColumnType<UIStorageItem> = {
  title: <span className="text-xs">Size</span>,
  dataIndex: 'size',
  key: 'size',
  width: 80,
  render: (value: string) => (value !== undefined ? displaySize(value) : ''),
};

export const storageItemDateChangedColumn: ColumnType<UIStorageItem> = {
  title: <span className="text-xs">Date changed</span>,
  dataIndex: 'changed',
  key: 'dateChanged',
  width: 200,
  render: (value: string) => displayDate(value),
};
