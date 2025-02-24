import type { ReactNode } from 'react';
import { useReducer } from 'react';
import { useEffect, useCallback, useMemo, useState } from 'react';
import type { StorageContext } from './storage-context';
import { storageContext } from './storage-context';
import type { FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import { useDataStorage, useDataStoragesStore } from '../../../state/storages/hooks.ts';
import type { StorageContents } from '../utils/storage-contents.ts';
import { StorageContentsLoader } from '../utils/storage-contents.ts';
import { DeleteEntityModal, ModalActionType, modalReducer, UpdateDataStorageEntityModal } from '../modals';
import { StorageModal, UpdateEntityModalMode } from '../constants.ts';
import { useDownloadFile } from '../hooks';
import type { UIStorageItem } from '../types.ts';
import { ROOT_PLACEHOLDER } from '../utils/navigation.ts';

export type StorageContextProps = {
  storageId: FindSingleDataStorageCriteria;
  path?: string;
  pageSize?: number;
  showArchived?: boolean;
  showVersions?: boolean;
  onPathChange?: (path?: string) => void;
  children?: ReactNode;
};

function correctPath(path: string | undefined): string {
  return !path || path.trim() === '' ? ROOT_PLACEHOLDER : path;
}

export function StorageContextProvider(props: StorageContextProps) {
  const {
    children,
    storageId: storageIdCriteria,
    path: pathProps,
    pageSize,
    onPathChange,
    showVersions,
    showArchived,
  } = props;
  const storage = useDataStorage(storageIdCriteria);
  const [path, setPath] = useState(pathProps);
  useEffect(() => {
    setPath(correctPath(pathProps));
  }, [storageIdCriteria, pathProps, setPath]);
  const { pending, error } = useDataStoragesStore();
  const [loader, setLoader] = useState<StorageContentsLoader | undefined>(undefined);
  const [contents, setContents] = useState<StorageContents | undefined>(undefined);
  const onChangePath = useCallback(
    (newPath: string | undefined): void => {
      if (onPathChange) {
        onPathChange(correctPath(newPath));
      }
      setPath(correctPath(newPath));
    },
    [onPathChange, setPath],
  );
  useEffect(() => {
    if (storage) {
      const aLoader = new StorageContentsLoader({
        storageId: storage.id,
        path,
        pageSize,
        listeners: [setContents],
        showArchived,
        showVersions,
      });
      setLoader(aLoader);
      return () => {
        setLoader(undefined);
        aLoader.destroy();
      };
    } else {
      setLoader(undefined);
    }
    return noop;
  }, [setLoader, storage, path, pageSize, showVersions, showArchived, setContents]);

  const reloadPage = useCallback(() => {
    if (loader) {
      void loader.reload();
    }
  }, [loader]);

  const loadNextPage = useCallback(() => {
    if (loader) {
      void loader.fetchNextPage();
    }
  }, [loader]);

  const [{ entityName, mode, openModal, pathToDelete, entityType }, dispatch] = useReducer(modalReducer, {
    openModal: null,
    mode: UpdateEntityModalMode.Update,
    entityType: undefined,
    entityName: '',
    pathToDelete: '',
  });

  const onEntityCreated = useCallback(() => {
    reloadPage();
    dispatch({ type: ModalActionType.RESET });
  }, [reloadPage]);

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
    reloadPage();
  }, [reloadPage]);

  const { handleDownload, downloadMessageContextHolder } = useDownloadFile(storage?.id);

  const onItemClick = useCallback(
    (item: UIStorageItem) => {
      if (item.type === DataStorageItemTypes.folder || item.type === 'navigateBack') {
        onChangePath(item.path || ROOT_PLACEHOLDER);
      }
    },
    [onChangePath],
  );

  const ctx = useMemo<StorageContext>(
    () => ({
      storage,
      path,
      onChangePath,
      pending,
      error,
      contents,
      onRowEditClick,
      onRowDeleteClick,
      handleDownload,
      openCreateModal,
      onItemClick,
      reloadPage,
      loadNextPage,
    }),
    [
      path,
      storage,
      pending,
      error,
      contents,
      onChangePath,
      handleDownload,
      onRowDeleteClick,
      onRowEditClick,
      openCreateModal,
      onItemClick,
      reloadPage,
      loadNextPage,
    ],
  );
  return (
    <storageContext.Provider value={ctx}>
      {children}
      {downloadMessageContextHolder}
      {storage && (
        <>
          <UpdateDataStorageEntityModal
            isOpen={openModal === StorageModal.Update}
            mode={mode}
            entityType={entityType}
            onOk={onEntityCreated}
            onCancel={() => dispatch({ type: ModalActionType.CLOSE })}
            path={path}
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
        </>
      )}
    </storageContext.Provider>
  );
}
