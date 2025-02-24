import { useCallback, useEffect, useMemo, useRef } from 'react';
import type { UIEvent as ReactUIEvent } from 'react';
import { Alert, Table } from 'antd';
import { DEFAULT_DATASTORAGE_PAGE_SIZE } from '@cloud-pipeline/api';
import { parentPath } from '@cloud-pipeline/core';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { ROOT_PLACEHOLDER } from '../utils/navigation';
import { getColumns } from '../utils';
import type { UIStorageItem } from '../types';
import { RowActions } from './row-actions';
import { useStorageContext } from '../context/storage-context.ts';
import './styles.css';

const ROW_HEIGHT = 40;
const INFINITE_SCROLL_OFFSET = 40;

export function StorageContentList() {
  const tableRef: Parameters<typeof Table>[0]['ref'] = useRef(null);
  const {
    onRowEditClick,
    onRowDeleteClick,
    onItemClick,
    loadNextPage,
    path: currentPath,
    contents,
  } = useStorageContext();
  const {
    items: content,
    pending,
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

  useEffect(() => {
    // tableRef.current?.scrollTo({ index: 0 });
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
      if (item.type === 'navigateBack') {
        return <div></div>;
      }

      return <RowActions onDelete={onRowDeleteClick} onEdit={onRowEditClick} item={item} />;
    },
    [onRowDeleteClick, onRowEditClick],
  );

  const columns = useMemo(() => getColumns(renderRowActions), [renderRowActions]);

  const onScroll = useCallback(
    (event: ReactUIEvent) => {
      const { scrollHeight, scrollTop, clientHeight } = event.target as HTMLElement;
      const bottom = scrollHeight - scrollTop - INFINITE_SCROLL_OFFSET < clientHeight;
      if (!pending && bottom && hasMoreItems) {
        loadNextPage();
      }
    },
    [loadNextPage, hasMoreItems, pending],
  );
  return (
    <div className="flex overflow-hidden h-full">
      <Table
        components={errorBodyOverride}
        className="storage-content-table"
        rowKey={(record) => `${record.type}_${record.name}`}
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
      />
    </div>
  );
}
