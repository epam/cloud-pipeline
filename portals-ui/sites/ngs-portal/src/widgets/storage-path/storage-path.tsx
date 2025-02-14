import { useCallback, useMemo, useState } from 'react';
import type { ChangeEvent, MouseEvent as ReactMouseEvent, KeyboardEvent as ReactKeyboardEvent } from 'react';
import { Input, Button, message, Breadcrumb, Dropdown } from 'antd';
import { ChevronDownIcon } from '@heroicons/react/24/outline';
import type { DataStorage, FindSingleDataStorageCriteria } from '@cloud-pipeline/core';
import { correctPath } from '@cloud-pipeline/core';
import { findDataStorage, FindDataStorageScope } from '@cloud-pipeline/core';
import {
  useDataStorage,
  useDataStoragesStore,
  useSearchDataStorages,
} from '../../state/storages/hooks.ts';
import type { CommonProps } from '@cloud-pipeline/components';
import classNames from 'classnames';

type Props = CommonProps & {
  storage: FindSingleDataStorageCriteria;
  path: string | undefined;
  onPathChange?: (value?: string) => void;
  onStorageChange?: (storage: DataStorage, path?: string) => void;
  storages?: Array<string | number | Partial<DataStorage>>;
};

export const StoragePath = ({
  className,
  style,
  storages: storagesCriteria,
  onPathChange,
  storage: storageSearchCriteria,
  onStorageChange,
  path = '',
}: Props) => {
  const { pending: storagesPending } = useDataStoragesStore();
  const storage = useDataStorage(storageSearchCriteria);
  const searchAllAvailable = useSearchDataStorages();
  const storages = useMemo(
    () => (storagesCriteria ? searchAllAvailable(storagesCriteria) : []),
    [searchAllAvailable, storagesCriteria],
  );
  const [rawValue, setRawValue] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [messageApi, contextHolder] = message.useMessage();

  const handleInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    setRawValue(e.target.value);
  };

  const leaveEditMode = useCallback(() => {
    const normalizedValue = rawValue.trim().toLowerCase();
    const processedValue = correctPath(normalizedValue.replace(/\/{2,}/g, '/'), {
      ensureTrailingSlash: true,
    });

    const match = /^([a-z0-9]+):\/\/([^/]+)(\/.*)?$/.exec(normalizedValue);

    if (match) {
      const bucketName = match[2];
      const aPath = match[3] || '/';

      const foundStorage = findDataStorage(storages, { criteria: normalizedValue, scope: FindDataStorageScope.path });

      if (foundStorage && onStorageChange) {
        onStorageChange(foundStorage, aPath);
      } else if (foundStorage) {
        messageApi.open({
          key: 'path-enter',
          type: 'error',
          content: (
            <span>
              Cannot navigate to <b>{bucketName}</b>
            </span>
          ),
        });
      } else {
        messageApi.open({
          key: 'path-enter',
          type: 'error',
          content: (
            <span>
              Storage <b>{bucketName}</b> is not in the list of available storages
            </span>
          ),
        });
      }
      setIsEditing(false);
      return;
    }
    if (onPathChange) {
      onPathChange(processedValue);
    }
    setIsEditing(false);
  }, [messageApi, onPathChange, onStorageChange, rawValue, storages]);

  const enterEditMode = useCallback(() => {
    setRawValue(path === '/' ? '' : path);
    setIsEditing(true);
  }, [path]);

  const handleBreadcrumbClick = useCallback(
    (e: ReactMouseEvent<HTMLElement, MouseEvent>, i: number) => {
      e.stopPropagation();
      const newPath = path
        .split('/')
        .filter(Boolean)
        .slice(0, i + 1)
        .join('/');
      if (onPathChange) {
        onPathChange(`/${newPath}/`);
      }
    },
    [onPathChange, path],
  );

  const resetPath = useCallback(() => {
    if (onPathChange) {
      onPathChange('/');
    }
  }, [onPathChange]);

  const handleInputKeydown = useCallback(
    (e: ReactKeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter') {
        leaveEditMode();
      }
    },
    [leaveEditMode],
  );

  const breadcrumbs = useMemo(() => {
    return path
      .split('/')
      .filter(Boolean)
      .map((item, i, arr) => ({
        key: item,
        title:
          i === arr.length - 1 ? (
            item
          ) : (
            <Button className="p-0" type="link" onClick={(e) => handleBreadcrumbClick(e, i)}>
              {item}
            </Button>
          ),
      }));
  }, [handleBreadcrumbClick, path]);

  const storagesDropDownItems = useMemo(
    () =>
      storages.map((st) => ({
        key: st.id.toString(),
        label: st.pathMask,
      })),
    [storages],
  );

  const onStorageDropDownClick = useCallback(
    (item: { key: string }) => {
      const newStorage = storages.find((o) => o.id === Number(item.key));
      if (newStorage && onStorageChange) {
        onStorageChange(newStorage);
      }
    },
    [storages, onStorageChange],
  );

  return (
    <div className={classNames(className, 'inline-flex', 'gap-1', 'items-center', 'w-full')} style={style}>
      {contextHolder}
      <div className="flex items-center cursor-pointer">
        {(storage || !storagesPending) && (
          <Button type="link" className="p-0" onClick={resetPath}>
            {storage ? storage.pathMask : 'unknown storage'}
          </Button>
        )}

        <Dropdown
          className="ml-2"
          menu={{
            items: storagesDropDownItems,
            onClick: onStorageDropDownClick,
          }}
          trigger={['click']}>
          <Button type="text" className="p-1">
            <ChevronDownIcon className="w-4 h-4" />
          </Button>
        </Dropdown>
      </div>
      {isEditing ? (
        <Input
          className="flex-grow px-4"
          style={{ flex: 1 }}
          value={rawValue}
          onChange={handleInputChange}
          onKeyDown={handleInputKeydown}
          onBlur={leaveEditMode}
          autoFocus
        />
      ) : (
        <Button className="flex-grow justify-start px-4 cursor-text" type="text" onClick={enterEditMode}>
          {breadcrumbs.length ? <Breadcrumb className="[&_li]:flex [&_li]:items-center" items={breadcrumbs} /> : '/'}
        </Button>
      )}
    </div>
  );
};
