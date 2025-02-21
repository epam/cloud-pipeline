import type { MenuProps } from 'antd';
import { Button, Dropdown } from 'antd';
import { ChevronDownIcon, PlusIcon } from '@heroicons/react/24/outline';
import { DocumentIcon, FolderIcon } from '@heroicons/react/24/outline';
import { DataStorageItemTypes } from '@cloud-pipeline/core';

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

type Props = {
  onMenuItemClick: (key: DataStorageItemTypes) => void;
};

export function HeaderActions({ onMenuItemClick }: Props) {
  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    onMenuItemClick(key as DataStorageItemTypes);
  };

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
