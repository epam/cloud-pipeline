import { Button, Dropdown } from 'antd';
import { ChevronDownIcon, PlusIcon } from '@heroicons/react/24/outline';
import { DocumentIcon, FolderIcon } from '@heroicons/react/24/outline';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import { useStorageContext } from '../context/storage-context.ts';
import { useCallback } from 'react';

const actions = [
  {
    key: DataStorageItemTypes.file,
    icon: <DocumentIcon className="w-4 h-4" />,
    label: 'File',
  },
  {
    key: DataStorageItemTypes.folder,
    icon: <FolderIcon className="w-4 h-4" />,
    label: 'Folder',
  },
];

export function HeaderActions() {
  const { onCreateItem } = useStorageContext();
  const onMenuClick = useCallback(
    (o: { key: string }) => {
      onCreateItem(o.key as DataStorageItemTypes);
    },
    [onCreateItem],
  );

  return (
    <div className="flex justify-end">
      <Dropdown menu={{ items: actions, onClick: onMenuClick }}>
        <Button size="small" type="primary">
          <PlusIcon className="size-3 stroke-2" />
          Create
          <ChevronDownIcon className="w-4 h-4" />
        </Button>
      </Dropdown>
    </div>
  );
}
