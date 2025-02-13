import { useCallback, useEffect } from 'react';
import { message } from 'antd';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { StorageContentList } from './storage-content-list';
import { useStorageNavigation } from './hooks/use-storage-navigation';
import { ROOT_PLACEHOLDER } from './utils/navigation';

type Props = {
  storageId: number;
  path?: string;
};

export function StorageBrowser({ storageId, path }: Props) {
  const {
    items,
    currentPath,
    changePath,
    navigatePrevPage,
    navigateNextPage,
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
    <div className="h-full flex overflow-hidden">
      {contextHolder}
      <StorageContentList
        content={items}
        onRowClick={onRowClick}
        currentPath={currentPath}
        pending={pending}
        onClickNextPage={navigateNextPage}
        onClickPrevPage={navigatePrevPage}
        paging={paging}
      />
    </div>
  );
}
