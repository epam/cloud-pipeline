import { useCallback, useEffect } from 'react';
import classNames from 'classnames';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import { StorageContentList } from './storage-content-list';
import { useStorageNavigation } from './hooks/use-storage-navigation';
import { ROOT_PLACEHOLDER } from './utils/navigation';
import HeaderActions from './components/header-actions';
import type { CommonProps } from '@cloud-pipeline/components';
import type { UIStorageItem } from './types';

type Props = CommonProps & {
  storageId: number;
  path?: string;
  onPathChange?: (path?: string) => void;
  showHeaderControls?: boolean;
};

export function StorageBrowser({ className, style, storageId, path, onPathChange, showHeaderControls }: Props) {
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
  } = useStorageNavigation(storageId);
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
    if (path && path !== currentPath) {
      navigate(path);
    }
  }, [navigate, currentPath, path]);
  return (
    <div className={classNames(className, 'inline-flex', 'flex-col', 'gap-2', 'overflow-hidden')} style={style}>
      {showHeaderControls ? (
        <HeaderActions currentPath={currentPath} storageId={storageId} refreshNavigation={refreshCurrentPath} />
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
        error={error}
      />
    </div>
  );
}
