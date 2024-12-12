import type { Pipeline } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import { PipelineCard } from './pipeline-card';
import { memo, useMemo, useState } from 'react';
import cn from 'classnames';
import { ShareIcon } from '@heroicons/react/24/outline';
import { NgsFilters } from '../../../features/ngs-filters';
import { useSearch } from '../../../shared/hooks/use-search';
import { pipelinesFiltersToDisplay } from '../../pipelines/constants';

type Props = {
  pipelines: Pipeline[];
  mode?: 'standard' | 'extended';
  showDescription?: boolean;
  withFilters?: boolean;
};

export const PipelinesList = memo(
  ({ pipelines, mode = 'standard', showDescription, withFilters }: Props) => {
    const {
      filtered: searchedPipelines,
      search,
      onSearchChange,
    } = useSearch({ items: pipelines });

    const [filteredPipelines, setFilteredPipelines] =
      useState(searchedPipelines);

    const beforeSearch = useMemo(() => {
      if (!withFilters) {
        return null;
      }

      return (
        <NgsFilters
          filtersToDisplay={pipelinesFiltersToDisplay}
          items={pipelines}
          searchedItems={searchedPipelines}
          onFilteredItemsChange={setFilteredPipelines}
        />
      );
    }, [pipelines, searchedPipelines, withFilters]);

    const renderItem = (item: Pipeline, search: string, i: number) => {
      return (
        <PipelineCard
          key={item.id}
          pipeline={item}
          highlightedText={search}
          className={cn({ ['border-t']: i !== 0 })}
          mode={mode}
          showDescription={showDescription}
        />
      );
    };

    return (
      <ItemsPanel
        className="max-h-full list-container overflow-auto"
        title={
          <div className="fill-current flex items-center flex-nowrap gap-1">
            <ShareIcon className="w-5 h-5 -rotate-90" />
            <span>Pipelines</span>
          </div>
        }
        items={withFilters ? filteredPipelines : searchedPipelines}
        render={renderItem}
        sliced
        virtualized
        search={search}
        beforeSearch={beforeSearch}
        onSearchChange={onSearchChange}
        itemKey="id"
        searchClassName={mode === 'extended' ? 'py-1' : undefined}
        viewAll={
          mode === 'extended'
            ? undefined
            : { title: 'View all pipelines', link: '/pipelines' }
        }
      />
    );
  },
);
