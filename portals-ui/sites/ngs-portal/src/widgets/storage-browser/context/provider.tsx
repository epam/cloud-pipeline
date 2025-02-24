import type { ReactNode } from 'react';
import { useReducer } from 'react';
import { useEffect, useCallback, useMemo, useState } from 'react';
import type { StorageContext } from './storage-context';
import { storageContext } from './storage-context';
import type { DataStorage, DataStorageItem, FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { DataStorageItemTypes } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import { useDataStorage, useDataStoragesStore, useSearchDataStorages } from '../../../state/storages/hooks.ts';
import { DeleteEntityModal, ModalActionType, modalReducer, UpdateDataStorageEntityModal } from '../modals';
import { StorageModal, UpdateEntityModalMode } from '../modals/constants.ts';
import { useDownloadFile } from '../hooks';
import type { StorageContentsData } from '../utils';
import { correctStoragePath, ROOT_PLACEHOLDER, StorageContentsDataLoader } from '../utils';
import type { UIStorageItem } from '../types.ts';

export type StorageContextProps = {
  storage: FindSingleDataStorageCriteria;
  onStorageChange?: (storage: DataStorage, path?: string) => void;
  storages?: Array<string | number | Partial<DataStorage>> | 'all';
  path?: string;
  pageSize?: number;
  showArchived?: boolean;
  showVersions?: boolean;
  onPathChange?: (path?: string) => void;
  selectedItems?: DataStorageItem[];
  onSelectionChanged?: (selection: DataStorageItem[]) => void;
};

export function StorageContextProvider(props: StorageContextProps & { children?: ReactNode }) {
  const {
    children,
    storages: storagesCriteria,
    onStorageChange,
    storage: storageIdCriteria,
    path: pathProps,
    pageSize,
    onPathChange,
    showVersions,
    showArchived,
    selectedItems,
    onSelectionChanged,
  } = props;
  const searchAllAvailable = useSearchDataStorages();
  const storages = useMemo(
    () => (storagesCriteria ? searchAllAvailable(storagesCriteria === 'all' ? undefined : storagesCriteria) : []),
    [searchAllAvailable, storagesCriteria],
  );
  const storage = useDataStorage(storageIdCriteria);
  const [path, setPath] = useState(pathProps);
  useEffect(() => {
    setPath(correctStoragePath(pathProps));
  }, [storageIdCriteria, pathProps, setPath]);
  const { pending, error } = useDataStoragesStore();
  const [loader, setLoader] = useState<StorageContentsDataLoader | undefined>(undefined);
  const [contents, setContents] = useState<StorageContentsData | undefined>(undefined);
  const onPathChangeCallback = useCallback(
    (newPath: string | undefined): void => {
      if (onPathChange) {
        onPathChange(correctStoragePath(newPath));
      }
      setPath(correctStoragePath(newPath));
    },
    [onPathChange, setPath],
  );
  const onStorageChangeCallback = useCallback(
    (storage: DataStorage, path?: string) => {
      if (onStorageChange) {
        onStorageChange(storage, path);
      }
    },
    [onStorageChange],
  );
  useEffect(() => {
    if (storage) {
      const aLoader = new StorageContentsDataLoader({
        storageId: storage.id,
        path,
        pageSize,
        listeners: [setContents],
        showArchived,
        showVersions,
      });
      setContents(aLoader.getData());
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

  const [{ item, openModal, mode }, dispatch] = useReducer(modalReducer, {
    openModal: null,
    mode: UpdateEntityModalMode.Update,
    item: undefined,
  });

  const onEntityCreated = useCallback(() => {
    reloadPage();
    dispatch({ type: ModalActionType.RESET });
  }, [reloadPage]);

  const onCreateItem = useCallback((entityType: DataStorageItemTypes) => {
    dispatch({
      type: ModalActionType.OPEN_UPDATE,
      payload: { mode: UpdateEntityModalMode.Create, item: { name: '', path: '', type: entityType } },
    });
  }, []);

  const onEditItem = useCallback((item: DataStorageItem) => {
    dispatch({
      type: ModalActionType.OPEN_UPDATE,
      payload: { mode: UpdateEntityModalMode.Update, item },
    });
  }, []);

  const onDeleteItem = useCallback((item: DataStorageItem) => {
    dispatch({
      type: ModalActionType.OPEN_DELETE,
      payload: { item },
    });
  }, []);

  const onDeleteSuccess = useCallback(() => {
    dispatch({ type: ModalActionType.CLOSE });
    reloadPage();
  }, [reloadPage]);

  const { onDownloadItem, downloadMessageContextHolder } = useDownloadFile(storage?.id);

  const onItemClick = useCallback(
    (item: UIStorageItem) => {
      if (item.type === DataStorageItemTypes.folder || item.type === 'navigateBack') {
        onPathChangeCallback(item.path || ROOT_PLACEHOLDER);
      }
    },
    [onPathChangeCallback],
  );

  const selectedStorageItems = useMemo(() => selectedItems ?? [], [selectedItems]);
  const onStorageItemsSelectionChanged = useCallback(
    (newItems: DataStorageItem[]) => {
      if (onSelectionChanged) {
        onSelectionChanged(newItems);
      }
    },
    [onSelectionChanged],
  );

  const selectionEnabled = typeof onSelectionChanged === 'function';

  const ctx = useMemo<StorageContext>(
    () => ({
      storage,
      storages,
      onStorageChange: onStorageChangeCallback,
      path,
      onChangePath: onPathChangeCallback,
      pending,
      error,
      contents,
      onEditItem,
      onDeleteItem,
      onDownloadItem,
      onCreateItem,
      onItemClick,
      reloadPage,
      loadNextPage,
      selectedItems: selectedStorageItems,
      onSelectionChanged: onStorageItemsSelectionChanged,
      selectionEnabled,
    }),
    [
      path,
      storage,
      storages,
      pending,
      error,
      contents,
      onPathChangeCallback,
      onDownloadItem,
      onDeleteItem,
      onEditItem,
      onCreateItem,
      onItemClick,
      reloadPage,
      loadNextPage,
      selectedStorageItems,
      onStorageItemsSelectionChanged,
      selectionEnabled,
      onStorageChangeCallback,
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
            onOk={onEntityCreated}
            onCancel={() => dispatch({ type: ModalActionType.CLOSE })}
            path={path}
            storageId={storage.id}
            item={item}
          />

          <DeleteEntityModal
            onDeleteSuccess={onDeleteSuccess}
            isOpen={openModal === StorageModal.Delete}
            onClose={() => dispatch({ type: ModalActionType.CLOSE })}
            storageId={storage.id}
            item={item}
          />
        </>
      )}
    </storageContext.Provider>
  );
}
