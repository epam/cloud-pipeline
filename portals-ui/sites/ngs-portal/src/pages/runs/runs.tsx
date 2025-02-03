import { useCallback, useState } from 'react';
import { Pagination } from 'antd';
import type { Run } from '@cloud-pipeline/core';
import { PlayCircleIcon } from '@heroicons/react/24/outline';
import cn from 'classnames';
import { useAuthenticatedUserProjectRuns } from '../../shared/hooks/use-runs-filter.ts';
import { ItemsPanel } from '../../widgets/items-panel';
import { RunCard } from '../../widgets/cards';

const pageSize = 25;

function runCardRenderer(item: Run, _: string, i: number) {
  return (
    <RunCard
      key={item.id}
      run={item}
      className={cn({ ['border-t']: i !== 0 })}
    />
  );
}

export function RunsPage() {
  const [page, setPage] = useState(0);
  const onPageChange = useCallback(
    (page: number) => {
      setPage(page - 1);
    },
    [setPage],
  );
  const { runs, error, pending, total } = useAuthenticatedUserProjectRuns({
    reloadIntervalMs: 5000,
    page: page + 1,
    pageSize,
  });

  return (
    <ItemsPanel<Run>
      className="h-full w-full list-container overflow-auto"
      render={runCardRenderer}
      title={
        <div className="min-h-6 fill-current flex items-center flex-nowrap gap-1">
          <PlayCircleIcon className="w-5 h-5" />
          <span>Runs history</span>
        </div>
      }
      items={runs}
      itemKey="id"
      isItemsLoading={pending && total === undefined}
      errorText={error && `Error: ${error}`}
      footer={
        total &&
        total > pageSize && (
          <Pagination
            current={page + 1}
            total={total ?? 0}
            onChange={onPageChange}
            size="small"
            pageSize={pageSize}
            showSizeChanger={false}
          />
        )
      }
    />
  );
}
