import { useMemo } from 'react';
import { Table } from 'antd';
import { DEFAULT_DATASTORAGE_PAGE_SIZE } from '@cloud-pipeline/api';
import { DataStorageItemTypes, parentPath } from '@cloud-pipeline/core';
import type { DataStorageItem } from '@cloud-pipeline/core';
import './styles.css';
import { ROOT_PLACEHOLDER } from './utils/navigation';
import StoragePagination from './storage-pagination';
import columns from './columns';
import type { StoragePaging } from './types';

const ROW_HEIGHT = 40;

type Props = {
  content?: DataStorageItem[];
  onRowClick?: (item: DataStorageItem) => void;
  currentPath: string | undefined;
  pending?: boolean;
  onClickNextPage: () => void;
  onClickPrevPage: () => void;
  paging: StoragePaging;
};

export function StorageContentList({
  content,
  onRowClick,
  currentPath,
  pending,
  onClickPrevPage,
  onClickNextPage,
  paging,
}: Props) {
  const dataSource = useMemo<DataStorageItem[]>(
    () =>
      [
        currentPath !== ROOT_PLACEHOLDER
          ? {
              type: DataStorageItemTypes.navigateBack as string,
              name: '...',
              path: parentPath(currentPath ?? ''),
            }
          : null,
        ...(content ?? []),
      ].filter(Boolean) as DataStorageItem[],
    [content, currentPath],
  );
  return (
    <div className="flex overflow-hidden h-full">
      <Table
        className="storage-content-table"
        rowKey={(record) => `${record.type}_${record.name}`}
        dataSource={dataSource}
        columns={columns}
        onRow={(record) => ({
          onClick: () => onRowClick?.(record),
        })}
        loading={pending}
        size="small"
        pagination={false}
        rowClassName="cursor-pointer"
        scroll={{ y: DEFAULT_DATASTORAGE_PAGE_SIZE * ROW_HEIGHT }}
        footer={() => (
          <StoragePagination
            onClickPrevPage={onClickPrevPage}
            onClickNextPage={onClickNextPage}
            paging={paging}
            pending={pending}
          />
        )}
      />
    </div>
  );
}
