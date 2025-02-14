import { useMemo } from 'react';
import { Alert, Table } from 'antd';
import { DEFAULT_DATASTORAGE_PAGE_SIZE } from '@cloud-pipeline/api';
import { parentPath } from '@cloud-pipeline/core';
import type { DataStorageItem } from '@cloud-pipeline/core';
import './styles.css';
import { ROOT_PLACEHOLDER } from './utils/navigation';
import StoragePagination from './storage-pagination';
import columns from './columns';
import type { StoragePaging, UIStorageItem } from './types';

const ROW_HEIGHT = 40;

type Props = {
  content?: DataStorageItem[];
  onRowClick?: (item: UIStorageItem) => void;
  currentPath: string | undefined;
  pending?: boolean;
  onClickNextPage: () => void;
  onClickPrevPage: () => void;
  onResetPaging: () => void;
  paging: StoragePaging;
  error?: string;
};

export function StorageContentList({
  content,
  onRowClick,
  currentPath,
  pending,
  onClickPrevPage,
  onClickNextPage,
  onResetPaging,
  paging,
  error,
}: Props) {
  const dataSource = useMemo<UIStorageItem[]>(
    () =>
      [
        currentPath !== ROOT_PLACEHOLDER
          ? {
              type: 'navigateBack',
              name: '...',
              path: parentPath(currentPath ?? ''),
            }
          : null,
        ...(content ?? []),
      ].filter((item) => item?.type && item?.name) as UIStorageItem[],
    [content, currentPath],
  );
  const errorBodyOverride = useMemo(() => {
    if (!error) {
      return undefined;
    }
    return {
      body: () => <Alert type="error" message={error} />,
    };
  }, [error]);
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
        size="small"
        pagination={false}
        rowClassName="cursor-pointer"
        scroll={{ y: DEFAULT_DATASTORAGE_PAGE_SIZE * ROW_HEIGHT }}
        footer={() => (
          <StoragePagination
            onClickPrevPage={onClickPrevPage}
            onClickNextPage={onClickNextPage}
            onResetPaging={onResetPaging}
            paging={paging}
            pending={pending}
          />
        )}
      />
    </div>
  );
}
