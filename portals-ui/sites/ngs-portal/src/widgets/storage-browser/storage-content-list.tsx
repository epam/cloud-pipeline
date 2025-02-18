import { useCallback, useMemo } from 'react';
import { Alert, Table } from 'antd';
import { DEFAULT_DATASTORAGE_PAGE_SIZE } from '@cloud-pipeline/api';
import { parentPath } from '@cloud-pipeline/core';
import type { DataStorageItem } from '@cloud-pipeline/core';
import './styles.css';
import { ROOT_PLACEHOLDER } from './utils/navigation';
import columns from './columns';
import type { StoragePaging, UIStorageItem } from './types';

const ROW_HEIGHT = 40;
const INFINITE_SCROLL_OFFSET = 40;

type Props = {
  content?: DataStorageItem[];
  onRowClick?: (item: UIStorageItem) => void;
  currentPath: string | undefined;
  prevCurrentPath: string | undefined;
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
  prevCurrentPath,
  pending,
  onClickNextPage,
  paging,
  error,
}: Props) {
  const dataSource = useMemo<UIStorageItem[]>(() => {
    const showNavigateBack = prevCurrentPath !== ROOT_PLACEHOLDER;
    return [
      showNavigateBack
        ? {
            type: 'navigateBack',
            name: '...',
            path: parentPath(currentPath ?? ''),
          }
        : null,
      ...(content ?? []),
    ].filter((item) => item?.type && item?.name) as UIStorageItem[];
  }, [content, currentPath, prevCurrentPath]);
  const errorBodyOverride = useMemo(() => {
    if (!error) {
      return undefined;
    }
    return {
      body: () => <Alert type="error" message={error} />,
    };
  }, [error]);
  const onScroll = useCallback(
    (event: React.UIEvent) => {
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
        size="small"
        pagination={false}
        rowClassName="cursor-pointer"
        scroll={{ y: DEFAULT_DATASTORAGE_PAGE_SIZE * ROW_HEIGHT }}
        onScroll={onScroll}
      />
    </div>
  );
}
