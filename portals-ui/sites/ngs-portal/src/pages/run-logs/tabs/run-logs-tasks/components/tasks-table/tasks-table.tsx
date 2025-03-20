import type { TableProps } from 'antd';
import { Table } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { calculateMinColWidth, getColumns, prepareTaskData } from '../../helpers';
import type { EngineTaskStatus, RunTasksData } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import cn from 'classnames';
import { StatusPill } from '../status-pill';
import type { ProcessedDataEntry, SelectedTask, SortingState } from '../../types';
import './style.css';
import { DEFAULT_DYNAMIC_COLUMNS } from '../../constants';
import { TaskDetailsModal } from './task-details-modal';

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
  dynamicColumns?: string[];
};

const headerHeight = 39;
const paginationHeight = 42;

export const TasksTable = ({
  data,
  isLoading,
  error,
  pagination,
  sorting,
  onPageSelect,
  onSortChange,
  dynamicColumns = DEFAULT_DYNAMIC_COLUMNS,
  className,
  style,
}: Props) => {
  const [scrollY, setScrollY] = useState(0);
  const [selectedTask, setSelectedTask] = useState<SelectedTask>();
  const tableContainerRef = useRef<HTMLDivElement>(null);

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
        setScrollY(containerHeight - headerHeight - paginationHeight);
      }
    };

    updateScrollY();
    window.addEventListener('resize', updateScrollY);

    return () => window.removeEventListener('resize', updateScrollY);
  }, []);

  const minColWidth = useMemo(() => {
    return calculateMinColWidth(dynamicColumns);
  }, [dynamicColumns]);

  const processedData = useMemo(() => prepareTaskData(data), [data]);

  const columns: TableProps<RunTasksData['elements'][number]>['columns'] = useMemo(
    () => getColumns({ dynamicColumns, minColWidth, sorting, renderStatus }),
    [dynamicColumns, minColWidth, sorting],
  );

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

  const handleRowClick = ({ taskKey, taskName }: ProcessedDataEntry) => {
    if (taskKey && taskName) {
      setSelectedTask({ taskKey, taskName });
    }
  };

  if (error) {
    return <div className="text-red-500">{error}</div>;
  }

  return (
    <div ref={tableContainerRef} className={cn('w-full overflow-auto', className)}>
      <Table
        loading={isLoading}
        className="tasks-table"
        style={style}
        pagination={{
          onChange: onPageSelect,
          style: { marginBottom: 0 },
          size: 'small',
          current: pagination.page,
          ...pagination,
        }}
        rowClassName={'cursor-pointer'}
        dataSource={processedData}
        columns={columns}
        rowKey="taskId"
        scroll={{ x: 'max-content', y: scrollY }}
        onChange={handleTableChange}
        onRow={(record) => ({
          onClick: () => handleRowClick(record),
        })}
      />
      <TaskDetailsModal isOpen={!!selectedTask} onClose={() => setSelectedTask(undefined)} task={selectedTask} />
    </div>
  );
};
