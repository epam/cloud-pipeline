import { useCallback, useEffect, useMemo, useRef } from 'react';
import type { Key, UIEvent as ReactUIEvent, ReactNode } from 'react';
import { Alert, Table } from 'antd';
import { DEFAULT_DATASTORAGE_PAGE_SIZE } from '@cloud-pipeline/api';
import { parentPath } from '@cloud-pipeline/core';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { ROOT_PLACEHOLDER } from '../utils/navigation';
import { getColumns } from '../utils';
import type { UIStorageItem } from '../types';
import { RowActions } from './row-actions';
import { useStorageContext } from '../context/storage-context.ts';
import { isDataStorageItem } from '../utils/misc.ts';
import './styles.css';

const ROW_HEIGHT = 40;
const INFINITE_SCROLL_OFFSET = 40;

type Props = {
  pending?: boolean;
  showItemActions?: boolean;
};

function getRowKey(storageItem: UIStorageItem): string {
  return `${storageItem.type}_${storageItem.path}`;
}

export function StorageContentList(props: Props) {
  const { pending: pendingProps, showItemActions = true } = props;
  const tableRef: Parameters<typeof Table>[0]['ref'] = useRef(null);
  const {
    onItemClick,
    loadNextPage,
    path: currentPath,
    contents,
    selectedItems,
    onSelectionChanged,
    selectionEnabled,
  } = useStorageContext();
  const {
    items: content,
    pending: contentsPending,
    hasMoreItems,
    error,
  } = useMemo(() => {
    if (contents) {
      return contents;
    }
    return {
      storageId: undefined,
      items: [] as DataStorageItem[],
      pages: [],
      pending: false,
      error: undefined,
      hasMoreItems: false,
    };
  }, [contents]);

  const pending = contentsPending || pendingProps;

  useEffect(() => {
    tableRef.current?.scrollTo({ top: 0 });
  }, [currentPath]);

  const dataSource = useMemo<UIStorageItem[]>(() => {
    const navigateBack: UIStorageItem | undefined =
      currentPath !== ROOT_PLACEHOLDER
        ? {
            type: 'navigateBack',
            name: '...',
            path: parentPath(currentPath ?? ''),
          }
        : undefined;

    return [...(navigateBack ? [navigateBack] : []), ...(content ?? [])];
  }, [content, currentPath]);

  const errorBodyOverride = useMemo(() => {
    if (!error) {
      return undefined;
    }
    return {
      body: () => <Alert type="error" message={error} />,
    };
  }, [error]);

  const renderRowActions = useCallback(
    (item: UIStorageItem) => {
      if (!showItemActions) {
        return <div></div>;
      }
      if (item.type === 'navigateBack') {
        return <div></div>;
      }
      return <RowActions item={item} />;
    },
    [showItemActions],
  );

  const columns = useMemo(() => getColumns(renderRowActions), [renderRowActions]);

  const onScroll = useCallback(
    (event: ReactUIEvent) => {
      const { scrollHeight, scrollTop, clientHeight } = event.target as HTMLElement;
      const bottom = scrollHeight - scrollTop - INFINITE_SCROLL_OFFSET < clientHeight;
      if (!pending && bottom && hasMoreItems) {
        console.log('scroll', pending, bottom, hasMoreItems);
        loadNextPage();
      }
    },
    [loadNextPage, hasMoreItems, pending],
  );

  const selectionConfig = useMemo(
    () =>
      selectionEnabled
        ? {
            preserveSelectedRowKeys: true,
            selectedRowKeys: selectedItems.map(getRowKey),
            hideSelectAll: true,
            onChange: (keys: Key[], selectedRows: UIStorageItem[]) => {
              const persisted = selectedItems.filter((item) => keys.includes(getRowKey(item)));
              const persistedKeys = persisted.map(getRowKey);
              const newSelectedItems = selectedRows
                .filter(isDataStorageItem)
                .filter((item) => keys.includes(getRowKey(item)) && !persistedKeys.includes(getRowKey(item)));
              onSelectionChanged(persisted.concat(newSelectedItems));
            },
            renderCell: (_value: boolean, record: UIStorageItem, _index: number, node: ReactNode) => {
              if (record.type === 'navigateBack') {
                return null;
              }
              return node;
            },
          }
        : undefined,
    [onSelectionChanged, selectedItems, selectionEnabled],
  );

  return (
    <div className="flex overflow-hidden h-full">
      <Table
        components={errorBodyOverride}
        className="storage-content-table"
        rowKey={getRowKey}
        dataSource={dataSource}
        columns={columns}
        onRow={(record) => ({
          onClick: () => onItemClick(record),
        })}
        loading={pending}
        ref={tableRef}
        size="small"
        pagination={false}
        rowClassName="cursor-pointer"
        scroll={{ y: DEFAULT_DATASTORAGE_PAGE_SIZE * ROW_HEIGHT }}
        onScroll={onScroll}
        rowSelection={selectionConfig}
      />
    </div>
  );
}
