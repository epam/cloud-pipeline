import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { ItemsPanel } from '../../../widgets/items-panel/index.ts';
import type { Run } from '@cloud-pipeline/core';
import { RunCard } from '../../../widgets/cards/index.ts';
import cn from 'classnames';
import { useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Pagination, Switch } from 'antd';
import {
  generatePipelineRoutePath,
  PipelineTabs,
} from '../../../shared/constants/routes.ts';
import { PipelineSearchParams } from '../constants';

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
  version: string;
  extended?: boolean;
};

export function PipelineRunsList({ pipelineId, extended, version }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [isAllVersions, setIsAllVersions] = useState(true);
  const [isFetching, setIsFetching] = useState(true);

  const activePage: number = useMemo(
    () => Number(searchParams.get(PipelineSearchParams.Page)) || 1,
    [searchParams],
  );

  const { runs, total, pending, error } = useAuthenticatedUserRuns({
    reloadIntervalMs: 5000,
    pipelineIds: [pipelineId],
    pageSize: extended ? 20 : 10,
    page: activePage,
    versions: isAllVersions ? undefined : [version],
  });

  const handleChangePage = useCallback(
    (page: number) => {
      setSearchParams((prev) => {
        const newSearchParams = new URLSearchParams(prev);
        newSearchParams.set(PipelineSearchParams.Page, `${page}`);
        return newSearchParams;
      });

      setIsFetching(true);
    },
    [setSearchParams],
  );

  const toggleVersionFilter = useCallback(() => {
    setIsAllVersions((prev) => !prev);
    setIsFetching(true);
  }, []);

  const viewAllProps = useMemo(() => {
    if (!extended) {
      return {
        title: 'View all runs',
        link: generatePipelineRoutePath(pipelineId, PipelineTabs.RunHistory),
      };
    }
  }, [extended, pipelineId]);

  const showTotal = useCallback(
    (total: number, range: number[]) =>
      `${range[0]}-${range[1]} out of ${total} runs`,
    [],
  );

  useEffect(() => {
    if (!pending) {
      setIsFetching(false);
    }
  }, [pending]);

  useEffect(() => {
    setIsFetching(true);
  }, [version, isAllVersions]);

  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center pb-2 border-b-2">
        <Switch checked={isAllVersions} onChange={toggleVersionFilter} />
        <p className="ml-2">Show all versions</p>
      </div>
      <ItemsPanel<Run>
        className="flex-grow bg-white overflow-auto"
        render={runCardRenderer}
        items={runs}
        itemKey="id"
        viewAll={viewAllProps}
        isItemsLoading={isFetching}
        errorText={error && `Error: ${error}`}
      />
      {extended && total !== undefined && (
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
