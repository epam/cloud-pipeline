import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { ItemsPanel } from '../../../widgets/items-panel';
import type { Run } from '@cloud-pipeline/core';
import { RunCard } from '../../../widgets/cards';
import cn from 'classnames';
import { useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pagination } from 'antd';
import {
  generateProjectRoutePath,
  ProjectTabs,
} from '../../../shared/constants/routes.ts';

enum ProjectSearchParams {
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
  projectId: number;
  extended?: boolean;
};

export function ProjectRunsList({ projectId, extended }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [isRunsLoading, setIsRunsLoading] = useState(true);

  const activePage: number =
    Number(searchParams.get(ProjectSearchParams.Page)) || 1;

  const { runs, total, pending, error } = useAuthenticatedUserRuns({
    reloadIntervalMs: 5000,
    projectIds: [projectId],
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

    const historyUrl = generateProjectRoutePath(projectId, ProjectTabs.History);

    return {
      title: 'View all runs',
      link: historyUrl,
    };
  }, [extended, projectId]);

  const showTotal = (total: number, range: number[]) =>
    `${range[0]}-${range[1]} out of ${total} runs`;

  useEffect(() => {
    if (!pending && total !== undefined) {
      setIsRunsLoading(false);
    }
  }, [pending, total]);

  return (
    <div className="h-full flex flex-col">
      <ItemsPanel<Run>
        className="flex-grow bg-white overflow-auto"
        render={runCardRenderer}
        items={runs}
        itemKey="id"
        viewAll={viewAllProps}
        isItemsLoading={isRunsLoading || total === undefined}
        errorText={error && `Error: ${error}`}
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
          showTotal={showTotal}
        />
      )}
    </div>
  );
}
