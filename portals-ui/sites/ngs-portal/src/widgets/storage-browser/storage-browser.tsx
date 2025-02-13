import { useCallback, useEffect } from 'react';
import { message } from 'antd';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { StorageContentList } from './storage-content-list';
import { useStorageNavigation } from './hooks/use-storage-navigation';
import { ROOT_PLACEHOLDER } from './utils/navigation';
import HeaderActions from './components/header-actions';

type Props = {
  storageId: number;
  path?: string;
  showHeaderControls?: boolean;
};

export function StorageBrowser({ storageId, path, showHeaderControls }: Props) {
  const {
    items,
    currentPath,
    changePath,
    navigatePrevPage,
    navigateNextPage,
    refreshCurrentPath,
    paging,
    pending,
    error,
  } = useStorageNavigation(storageId);
  const [messageApi, contextHolder] = message.useMessage();
  useEffect(() => {
    if (error) {
      messageApi.open({
        key: 'datastorage-loading-error',
        type: 'error',
        content: error,
      });
    }
  }, [error, messageApi]);
  const onRowClick = useCallback(
    (item: DataStorageItem) => {
      if (
        item.type === DataStorageItemTypes.folder ||
        item.type === DataStorageItemTypes.navigateBack
      ) {
        changePath(item.path || ROOT_PLACEHOLDER);
      }
    },
    [changePath],
  );
  useEffect(() => {
    if (path && path !== currentPath) {
      changePath(path);
    }
  }, [changePath, currentPath, path]);
  return (
    <div className="h-full flex flex-col gap-2 overflow-hidden">
      {contextHolder}
      {showHeaderControls ? (
        <HeaderActions
          currentPath={currentPath}
          storageId={storageId}
          refreshNavigation={refreshCurrentPath}
        />
      ) : null}
      <StorageContentList
        content={items}
        onRowClick={onRowClick}
        currentPath={currentPath}
        pending={pending}
        onClickNextPage={navigateNextPage}
        onClickPrevPage={navigatePrevPage}
        onResetPaging={refreshCurrentPath}
        paging={paging}
      />
    </div>
  );
}
