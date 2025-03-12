import type { EngineTaskStatus } from '@cloud-pipeline/core';
import type { ReactNode } from 'react';
import type { SortingState } from '../types';

type GetColumnsProps = {
  dynamicColumns: string[];
  minColWidth: number;
  sorting?: SortingState;
  renderStatus: (status: EngineTaskStatus) => ReactNode;
};

type GetColumnsDefinitionProps = {
  title: string;
  key: string;
  sorting?: SortingState;
  sorter?: boolean;
};

const getColumnDefinition = ({ title, key, sorting, sorter }: GetColumnsDefinitionProps) => {
  return {
    title,
    dataIndex: key,
    key,
    ...(sorter && {
      sorter: true,
      sortOrder: sorting?.column === key ? sorting?.order : undefined,
    }),
  };
};

export const getColumns = ({ dynamicColumns, minColWidth, sorting, renderStatus }: GetColumnsProps) => {
  return [
    {
      ...getColumnDefinition({ title: 'ID', key: 'taskId', sorting, sorter: true }),
      width: 50,
    },
    {
      ...getColumnDefinition({ title: 'Process', key: 'taskGroup', sorting, sorter: true }),
    },
    {
      ...getColumnDefinition({ title: 'Tag', key: 'taskTag', sorting, sorter: true }),
      width: 50,
    },
    {
      ...getColumnDefinition({ title: 'Status', key: 'status', sorting, sorter: true }),
      render: renderStatus,
    },
    {
      ...getColumnDefinition({ title: 'Started', key: 'started' }),
    },
    {
      ...getColumnDefinition({ title: 'Finished', key: 'finished' }),
    },
    ...dynamicColumns.map((key) => ({
      title: key,
      dataIndex: key,
      key: key,
      width: minColWidth,
    })),
  ];
};
