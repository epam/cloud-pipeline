import type { TableProps } from 'antd';
import { Table } from 'antd';
import { useMemo } from 'react';
import { prepareTaskData } from '../helpers';
import type { EngineTaskStatus, RunTasksData } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import cn from 'classnames';
import { StatusPill } from './status-pill';
import type { SortingState } from '../types';

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
  onPageSelect,
  sorting,
  onSortChange,
  className,
  style,
}: Props) => {
  const { processedData, dynamicKeys } = useMemo(() => prepareTaskData(data), [data]);

  const renderStatus = (status: EngineTaskStatus) => {
    return (
      <div className="flex items-center">
        <StatusPill status={status} />
      </div>
    );
  };

  const columns: TableProps<RunTasksData['elements'][number]>['columns'] = [
    {
      title: 'ID',
      dataIndex: 'taskId',
      key: 'taskId',
      sorter: true,
      sortOrder: sorting?.column === 'taskId' ? sorting?.order : undefined,
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
      sorter: true,
      sortOrder: sorting?.column === 'started' ? sorting?.order : undefined,
    },
    {
      title: 'Finished',
      dataIndex: 'finished',
      key: 'finished',
      sorter: true,
      sortOrder: sorting?.column === 'finished' ? sorting?.order : undefined,
    },
    ...dynamicKeys.map((key) => ({
      title: key,
      dataIndex: key,
      key: key,
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
    <Table
      loading={isLoading}
      className={cn('w-full', className)}
      style={style}
      pagination={{ onChange: onPageSelect, ...pagination }}
      dataSource={processedData}
      columns={columns}
      rowKey="id"
      scroll={{ x: 'max-content' }}
      onChange={handleTableChange}
    />
  );
};
