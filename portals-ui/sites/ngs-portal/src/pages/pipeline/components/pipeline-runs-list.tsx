import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { ItemsPanel } from '../../../widgets/items-panel/index.ts';
import type { Run } from '@cloud-pipeline/core';
import { RunCard } from '../../../widgets/cards/index.ts';
import cn from 'classnames';
import { useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pagination } from 'antd';
import { PageSpinner } from '../../../shared/ui/index.ts';
import {
  generatePipelineRoutePath,
  PipelineTabs,
} from '../../../shared/constants/routes.ts';

enum PipelineSearchParams {
  Page = 'page',
}

function runCardRenderer(item: Run, _: string, i: number) {
  return (
    <RunCard
      key={item.id}
      run={item}
      className={cn({ ['border-t']: i !== 0 })}
    />
  );
}

type Props = {
  pipelineId: number;
  extended?: boolean;
};

export function PipelineRunsList({ pipelineId, extended }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [isRunsLoading, setIsRunsLoading] = useState(true);

  const activePage: number =
    Number(searchParams.get(PipelineSearchParams.Page)) || 1;

  const { runs, total, pending, error } = useAuthenticatedUserRuns({
    reloadIntervalMs: 5000,
    pipelineIds: [pipelineId],
    pageSize: extended ? 20 : 10,
    page: activePage,
  });

  const handleChangePage = useCallback(
    (page: number) => {
      const newSearchParams = new URLSearchParams(searchParams);
      newSearchParams.set(PipelineSearchParams.Page, `${page}`);
      setSearchParams(newSearchParams);
      setIsRunsLoading(true);
    },
    [searchParams, setSearchParams],
  );

  const viewAllProps = useMemo(() => {
    if (extended) {
      return undefined;
    }

    const historyUrl = generatePipelineRoutePath(
      pipelineId,
      PipelineTabs.RunHistory,
    );

    return {
      title: 'View all runs',
      link: historyUrl,
    };
  }, [extended, pipelineId]);

  const showTotal = (total: number, range: number[]) =>
    `${range[0]}-${range[1]} out of ${total} runs`;

  useEffect(() => {
    if (!pending && total !== undefined) {
      setIsRunsLoading(false);
    }
  }, [pending, total]);

  if (error) {
    return <div>Error: {error}</div>;
  }

  return (
    <div className="h-full flex flex-col">
      {isRunsLoading || total === undefined ? (
        <PageSpinner />
      ) : (
        <ItemsPanel<Run>
          className="flex-grow bg-white overflow-auto"
          render={runCardRenderer}
          items={runs}
          itemKey="id"
          viewAll={viewAllProps}
        />
      )}
      {extended && (
        <Pagination
          align="end"
          current={activePage}
          total={total}
          onChange={handleChangePage}
          size="small"
          pageSize={20}
          showSizeChanger={false}
          showTotal={showTotal}
        />
      )}
    </div>
  );
}
