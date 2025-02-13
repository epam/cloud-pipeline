import { useCallback, useState } from 'react';
import type { MenuProps } from 'antd';
import { Dropdown } from 'antd';
import { PlusIcon } from '@heroicons/react/24/outline';
import { DocumentIcon, FolderIcon } from '@heroicons/react/24/outline';
import CreateDataStorageEntityModal from '../modals/create-datastorage-entity-nodal';
import { DataStorageItemTypes } from '@cloud-pipeline/core';

const actions = [
  {
    key: DataStorageItemTypes.file as const,
    icon: <DocumentIcon className="w-4 h-4" />,
    label: 'File',
  },
  {
    key: DataStorageItemTypes.folder as const,
    icon: <FolderIcon className="w-4 h-4" />,
    label: 'Folder',
  },
];

type Props = {
  currentPath: string | undefined;
  storageId: number;
  refreshNavigation: () => void;
};

export default function HeaderActions({
  currentPath,
  storageId,
  refreshNavigation,
}: Props) {
  const [createEntity, setCreateEntity] = useState<
    DataStorageItemTypes | undefined
  >();
  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    setCreateEntity(key as DataStorageItemTypes);
  };
  const onEntityCreated = useCallback(() => {
    if (refreshNavigation) {
      refreshNavigation();
    }
    setCreateEntity(undefined);
  }, [refreshNavigation]);
  return (
    <div className="flex justify-end">
      <Dropdown.Button
        className="w-fit"
        size="small"
        type="primary"
        overlayClassName="w-40"
        menu={{ items: actions, onClick: onMenuClick }}>
        <PlusIcon className="w-4 h-4" />
        Create
      </Dropdown.Button>
      <CreateDataStorageEntityModal
        createEntityType={createEntity}
        onOk={onEntityCreated}
        onCancel={() => setCreateEntity(undefined)}
        path={currentPath}
        storageId={storageId}
      />
    </div>
  );
}
