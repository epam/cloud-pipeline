import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { ItemsPanel } from '../../../widgets/items-panel';
import type { Run } from '@cloud-pipeline/core';
import { RunCard } from '../../../widgets/cards';
import cn from 'classnames';
import { useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pagination } from 'antd';
import { ProjectSearchParams } from '../constants';
import { PageSpinner } from '../../../shared/ui';

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
  projectId?: number;
  extended?: boolean;
};

export function ProjectRunsList({ projectId, extended }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [isRunsLoading, setIsRunsLoading] = useState(true);

  const activePage: number =
    Number(searchParams.get(ProjectSearchParams.Page)) || 1;

  const { runs, total, pending, error } = useAuthenticatedUserRuns({
    reloadIntervalMs: 5000,
    projectIds: projectId ? [projectId] : undefined,
    pageSize: extended ? 20 : 10,
    page: activePage,
  });

  const handleChangePage = useCallback(
    (page: number) => {
      const newSearchParams = new URLSearchParams(searchParams);
      newSearchParams.set(ProjectSearchParams.Page, `${page}`);
      setSearchParams(newSearchParams);
      setIsRunsLoading(true);
    },
    [searchParams, setSearchParams],
  );

  const viewAllProps = useMemo(() => {
    if (extended) {
      return undefined;
    }

    const updatedSearchParams = new URLSearchParams(searchParams);
    updatedSearchParams.set('tab', 'history');

    const historyUrl = `/projects/${projectId}?${updatedSearchParams.toString()}`;

    return {
      title: 'View all runs',
      link: historyUrl,
    };
  }, [extended, projectId, searchParams]);

  useEffect(() => {
    if (!pending && runs.length) {
      setIsRunsLoading(false);
    }
  }, [pending, runs.length]);

  if (isRunsLoading) {
    return <PageSpinner />;
  }

  if (error) {
    return <div>Error: {error}</div>;
  }

  return (
    <div className="max-h-full flex flex-col">
      <ItemsPanel<Run>
        className="flex-grow bg-white overflow-auto"
        render={runCardRenderer}
        items={runs}
        itemKey="id"
        viewAll={viewAllProps}
      />
      {extended && (
        <Pagination
          align="end"
          current={activePage}
          total={total}
          onChange={handleChangePage}
          size="small"
          pageSize={20}
          showSizeChanger={false}
          showTotal={(total, range) =>
            `${range[0]}-${range[1]} out of ${total} runs`
          }
        />
      )}
    </div>
  );
}
