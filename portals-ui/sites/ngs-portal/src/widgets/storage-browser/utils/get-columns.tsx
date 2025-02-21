import { DataStorageItemTypes, displaySize, displayDate } from '@cloud-pipeline/core';
import { DocumentIcon, FolderIcon } from '@heroicons/react/24/outline';
import type { UIStorageItem } from '../types';
import type { ReactElement } from 'react';

export const getColumns = (renderRowActions: (item: UIStorageItem) => ReactElement) => [
  {
    dataIndex: 'type',
    key: 'type',
    width: 20,
    render: (value: UIStorageItem['type']) => {
      if (value === DataStorageItemTypes.file) {
        return <DocumentIcon className="w-4 h-4" />;
      }
      if (value === DataStorageItemTypes.folder || value === 'navigateBack') {
        return <FolderIcon className="w-4 h-4" />;
      }
    },
  },
  {
    title: <span className="text-xs">Name</span>,
    dataIndex: 'name',
    key: 'name',
    width: 200,
  },
  {
    title: <span className="text-xs">Size</span>,
    dataIndex: 'size',
    key: 'size',
    width: 80,
    render: (value: string) => (value !== undefined ? displaySize(value) : ''),
  },
  {
    title: <span className="text-xs">Date changed</span>,
    dataIndex: 'changed',
    key: 'dateChanged',
    width: 200,
    render: (value: string) => displayDate(value),
  },
  {
    key: 'actions',
    width: 90,
    render: (item: UIStorageItem) => renderRowActions(item),
  },
];
