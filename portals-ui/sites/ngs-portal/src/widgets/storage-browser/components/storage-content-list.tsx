import { useCallback, useEffect, useMemo, useRef } from 'react';
import type { UIEvent as ReactUIEvent } from 'react';
import { Alert, Table } from 'antd';
import { DEFAULT_DATASTORAGE_PAGE_SIZE } from '@cloud-pipeline/api';
import { parentPath } from '@cloud-pipeline/core';
import type { DataStorageItem, DataStorageItemTypes } from '@cloud-pipeline/core';
import { ROOT_PLACEHOLDER } from '../utils/navigation';
import { StoragePagination } from './storage-pagination';
import { getColumns } from '../utils';
import type { StoragePaging, UIStorageItem } from '../types';
import { RowActions } from './row-actions';
import './styles.css';

const ROW_HEIGHT = 40;
const INFINITE_SCROLL_OFFSET = 40;

type Props = {
  content?: DataStorageItem[];
  onRowClick?: (item: UIStorageItem) => void;
  currentPath: string | undefined;
  pending?: boolean;
  onClickNextPage: () => void;
  onClickPrevPage: () => void;
  onResetPaging: () => void;
  paging: StoragePaging;
  storageId: number;
  onRowEditClick: (key: DataStorageItemTypes, name: string) => void;
  onRowDeleteClick: (key: DataStorageItemTypes, name: string, path: string) => void;
  onRowDownloadClick: (name: string, path: string) => void;
  error?: string;
};

export function StorageContentList({
  content,
  onRowClick,
  currentPath,
  pending,
  onClickNextPage,
  onResetPaging,
  paging,
  error,
  onRowEditClick,
  onRowDeleteClick,
  onRowDownloadClick,
  storageId,
}: Props) {
  const tableRef: Parameters<typeof Table>[0]['ref'] = useRef(null);
  useEffect(() => {
    tableRef.current?.scrollTo({ index: 0 });
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

      return (
        <RowActions
          onDelete={onRowDeleteClick}
          onEdit={onRowEditClick}
          item={item}
          storageId={storageId}
          onResetPaging={onResetPaging}
          onDownload={onRowDownloadClick}
        />
      );
    },
    [onResetPaging, onRowDeleteClick, onRowDownloadClick, onRowEditClick, storageId],
  );

  const columns = useMemo(() => getColumns(renderRowActions), [renderRowActions]);

  const onScroll = useCallback(
    (event: ReactUIEvent) => {
      const { scrollHeight, scrollTop, clientHeight } = event.target as HTMLElement;
      const bottom = scrollHeight - scrollTop - INFINITE_SCROLL_OFFSET < clientHeight;
      if (!pending && bottom && paging.canNavigateNext) {
        onClickNextPage();
      }
    },
    [onClickNextPage, paging.canNavigateNext, pending],
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
          onClick: () => onRowClick?.(record),
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
