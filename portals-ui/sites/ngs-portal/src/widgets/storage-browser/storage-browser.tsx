import { useCallback, useEffect, useReducer } from 'react';
import classNames from 'classnames';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import type { FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { useStorageNavigation } from './hooks/use-storage-navigation';
import { ROOT_PLACEHOLDER } from './utils/navigation';
import { HeaderActions, StorageContentList } from './components';
import type { CommonProps } from '@cloud-pipeline/components';
import { useDataStorage } from '../../state/storages/hooks.ts';
import type { UIStorageItem } from './types';
import { UpdateDataStorageEntityModal, DeleteEntityModal, ModalActionType, modalReducer } from './modals';
import { useDownloadFile } from './hooks';
import { StorageModal, UpdateEntityModalMode } from './constants.ts';

type Props = CommonProps & {
  storageId: FindSingleDataStorageCriteria;
  path?: string;
  onPathChange?: (path?: string) => void;
  showHeaderControls?: boolean;
};

export function StorageBrowser({
  className,
  style,
  storageId: storageIdCriteria,
  path,
  onPathChange,
  showHeaderControls,
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

  const [{ entityName, mode, openModal, pathToDelete, entityType }, dispatch] = useReducer(modalReducer, {
    openModal: null,
    mode: UpdateEntityModalMode.Update,
    entityType: undefined,
    entityName: '',
    pathToDelete: '',
  });

  const onEntityCreated = useCallback(() => {
    refreshCurrentPath();
    dispatch({ type: ModalActionType.RESET });
  }, [refreshCurrentPath]);

  const openCreateModal = useCallback((entityType: DataStorageItemTypes) => {
    dispatch({
      type: ModalActionType.OPEN_UPDATE,
      payload: { mode: UpdateEntityModalMode.Create, entityType, entityName: '' },
    });
  }, []);

  const onRowEditClick = useCallback((entityType: DataStorageItemTypes, name: string) => {
    dispatch({
      type: ModalActionType.OPEN_UPDATE,
      payload: { mode: UpdateEntityModalMode.Update, entityType, entityName: name },
    });
  }, []);

  const onRowDeleteClick = useCallback((entityType: DataStorageItemTypes, entityName: string, path: string) => {
    dispatch({ type: ModalActionType.OPEN_DELETE, payload: { entityType, entityName, pathToDelete: path } });
  }, []);

  const onDeleteSuccess = useCallback(() => {
    dispatch({ type: ModalActionType.CLOSE });
    refreshCurrentPath();
  }, [refreshCurrentPath]);

  const { handleDownload, downloadMessageContextHolder, isDownloading } = useDownloadFile(storage?.id);

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

  if (!storage) {
    return <div>Storage not found</div>;
  }

  return (
    <div className={classNames(className, 'inline-flex', 'flex-col', 'gap-2', 'overflow-hidden')} style={style}>
      {downloadMessageContextHolder}
      {showHeaderControls ? <HeaderActions onMenuItemClick={openCreateModal} /> : null}
      <StorageContentList
        content={items}
        onRowClick={onRowClick}
        currentPath={currentPath}
        pending={pending || isDownloading}
        onClickNextPage={navigateNextPage}
        onClickPrevPage={navigatePrevPage}
        onResetPaging={refreshCurrentPath}
        paging={paging}
        error={error}
        storageId={storage.id}
        onRowEditClick={onRowEditClick}
        onRowDeleteClick={onRowDeleteClick}
        onRowDownloadClick={handleDownload}
      />

      <UpdateDataStorageEntityModal
        isOpen={openModal === StorageModal.Update}
        mode={mode}
        entityType={entityType}
        onOk={onEntityCreated}
        onCancel={() => dispatch({ type: ModalActionType.CLOSE })}
        path={currentPath}
        storageId={storage.id}
        entityName={entityName}
      />

      <DeleteEntityModal
        onDeleteSuccess={onDeleteSuccess}
        isOpen={openModal === StorageModal.Delete}
        onClose={() => dispatch({ type: ModalActionType.CLOSE })}
        entityName={entityName}
        entityType={entityType}
        storageId={storage.id}
        path={pathToDelete}
      />
    </div>
  );
}
