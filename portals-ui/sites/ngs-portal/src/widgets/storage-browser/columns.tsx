import {
  DataStorageItemTypes,
  displaySize,
  displayDate,
} from '@cloud-pipeline/core';
import { DocumentIcon, FolderIcon } from '@heroicons/react/24/outline';

const columns = [
  {
    dataIndex: 'type',
    key: 'type',
    width: 50,
    render: (value: DataStorageItemTypes) => {
      if (value === DataStorageItemTypes.file) {
        return <DocumentIcon className="w-4 h-4" />;
      }
      if (
        value === DataStorageItemTypes.folder ||
        value === DataStorageItemTypes.navigateBack
      ) {
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
    render: (value: string) => (value ? displaySize(value) : ''),
  },
  {
    title: <span className="text-xs">Date changed</span>,
    dataIndex: 'changed',
    key: 'dateChanged',
    width: 200,
    render: (value: string) => displayDate(value),
  },
];

export default columns;
