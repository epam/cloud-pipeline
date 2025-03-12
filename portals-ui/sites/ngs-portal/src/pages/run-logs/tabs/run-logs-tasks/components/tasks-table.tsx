import type { TableProps } from 'antd';
import { Table } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { calculateMinColWidth, prepareTaskData } from '../helpers';
import type { EngineTaskStatus, RunTasksData } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import cn from 'classnames';
import { StatusPill } from './status-pill';
import type { SortingState } from '../types';
import './style.css';

type Pagination = {
  pageSize: number;
  total: number;
  page: number;
};

type Props = CommonProps & {
  pagination: Pagination;
  data: RunTasksData['elements'];
  isLoading?: boolean;
  error?: string;
  onPageSelect: (page: number) => void;
  onSortChange: (sorting?: SortingState) => void;
  sorting?: SortingState;
};

export const TasksTable = ({
  data,
  isLoading,
  error,
  pagination,
  sorting,
  onPageSelect,
  onSortChange,
  className,
  style,
}: Props) => {
  const [scrollY, setScrollY] = useState(0);
  const tableContainerRef = useRef<HTMLDivElement>(null);

  const { processedData, dynamicKeys } = useMemo(() => prepareTaskData(data), [data]);

  const renderStatus = (status: EngineTaskStatus) => {
    return (
      <div className="flex items-center">
        <StatusPill status={status} />
      </div>
    );
  };

  useEffect(() => {
    const updateScrollY = () => {
      if (tableContainerRef.current) {
        const containerHeight = tableContainerRef.current.clientHeight;
        const headerHeight = 39;
        const paginationHeight = 42;
        setScrollY(containerHeight - headerHeight - paginationHeight);
      }
    };

    updateScrollY();
  }, []);

  const minColWidth = useMemo(() => {
    return calculateMinColWidth(dynamicKeys);
  }, [dynamicKeys]);

  const columns: TableProps<RunTasksData['elements'][number]>['columns'] = [
    {
      title: 'ID',
      dataIndex: 'taskId',
      key: 'taskId',
      sorter: true,
      sortOrder: sorting?.column === 'taskId' ? sorting?.order : undefined,
      width: 50,
    },
    {
      title: 'Process',
      dataIndex: 'taskGroup',
      key: 'taskGroup',
      sorter: true,
      sortOrder: sorting?.column === 'taskGroup' ? sorting?.order : undefined,
    },
    {
      title: 'Tag',
      dataIndex: 'taskTag',
      key: 'taskTag',
      sorter: true,
      sortOrder: sorting?.column === 'taskTag' ? sorting?.order : undefined,
      width: 50,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      sorter: true,
      sortOrder: sorting?.column === 'status' ? sorting?.order : undefined,
      render: renderStatus,
    },
    {
      title: 'Started',
      dataIndex: 'started',
      key: 'started',
    },
    {
      title: 'Finished',
      dataIndex: 'finished',
      key: 'finished',
    },
    ...dynamicKeys.map((key) => ({
      title: key,
      dataIndex: key,
      key: key,
      width: minColWidth,
    })),
  ];

  const handleTableChange: TableProps<RunTasksData['elements'][number]>['onChange'] = (
    _pagination,
    _filters,
    sorter,
  ) => {
    const isSortingChanged =
      !Array.isArray(sorter) && (sorter.columnKey !== sorting?.column || sorter.order !== sorting?.order);

    if (isSortingChanged) {
      if (sorter.columnKey && sorter.order) {
        onSortChange({
          column: sorter.columnKey as string,
          order: sorter.order,
        });
        return;
      }

      onSortChange();
    }
  };

  if (error) {
    return <div className="text-red-500">{error}</div>;
  }

  return (
    <div ref={tableContainerRef} className={cn('w-full overflow-auto', className)}>
      <Table
        loading={isLoading}
        className={'tasks-table'}
        style={style}
        pagination={{
          onChange: onPageSelect,
          style: { marginBottom: 0 },
          size: 'small',
          current: pagination.page,
          ...pagination,
        }}
        dataSource={processedData}
        columns={columns}
        rowKey="id"
        scroll={{ x: 'max-content', y: scrollY }}
        onChange={handleTableChange}
      />
    </div>
  );
};
