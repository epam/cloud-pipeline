import { useCallback, useEffect } from 'react';
import classNames from 'classnames';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import type { FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { StorageContentList } from './storage-content-list';
import { useStorageNavigation } from './hooks/use-storage-navigation';
import { ROOT_PLACEHOLDER } from './utils/navigation';
import HeaderActions from './components/header-actions';
import type { CommonProps } from '@cloud-pipeline/components';
import { useDataStorage } from '../../state/storages/hooks.ts';
import type { UIStorageItem } from './types';
import { Empty } from 'antd';

type Props = CommonProps & {
  storageId: FindSingleDataStorageCriteria;
  path?: string;
  onPathChange?: (path?: string) => void;
  showHeaderControls?: boolean;
  selection?: UIStorageItem[];
  onSelectItem?: (selection: UIStorageItem[]) => void;
  pending?: boolean;
};

export function StorageBrowser({
  className,
  style,
  storageId: storageIdCriteria,
  path,
  onPathChange,
  showHeaderControls,
  selection,
  onSelectItem,
  pending: pendingProp,
}: Props) {
  const storage = useDataStorage(storageIdCriteria);
  const {
    items,
    currentPath,
    changePath: navigate,
    navigatePrevPage,
    navigateNextPage,
    refreshCurrentPath,
    paging,
    pending,
    error,
  } = useStorageNavigation(storage?.id);
  const changePath = useCallback(
    (aPath: string) => {
      if (onPathChange) {
        onPathChange(aPath);
      } else {
        navigate(aPath);
      }
    },
    [navigate, onPathChange],
  );
  const onRowClick = useCallback(
    (item: UIStorageItem) => {
      if (item.type === DataStorageItemTypes.folder || item.type === 'navigateBack') {
        changePath(item.path || ROOT_PLACEHOLDER);
      }
    },
    [changePath],
  );
  useEffect(() => {
    if (path !== undefined && path !== currentPath) {
      navigate(path);
    }
  }, [navigate, currentPath, path]);
  return (
    <div className={classNames(className, 'inline-flex', 'flex-col', 'gap-2', 'overflow-hidden')} style={style}>
      {showHeaderControls && storage ? (
        <HeaderActions currentPath={currentPath} storageId={storage.id} refreshNavigation={refreshCurrentPath} />
      ) : null}
      {storage ? (
        <StorageContentList
          content={items}
          onRowClick={onRowClick}
          currentPath={currentPath}
          pending={pendingProp ?? pending}
          onClickNextPage={navigateNextPage}
          onClickPrevPage={navigatePrevPage}
          onResetPaging={refreshCurrentPath}
          paging={paging}
          error={error}
          selection={selection}
          onSelectItem={onSelectItem}
        />
      ) : (
        <Empty />
      )}
    </div>
  );
}
