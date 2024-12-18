import type { Pipeline } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import { PipelineCard } from './pipeline-card';
import { memo } from 'react';
import cn from 'classnames';
import { ShareIcon } from '@heroicons/react/24/outline';
import { pipelinesFiltersToDisplay } from '../../pipelines/constants';
import { NgsFilters, useNgsFilters } from '../../../features/ngs-filters';

type Props = {
  pipelines: Pipeline[];
  mode?: 'standard' | 'extended';
  showDescription?: boolean;
  withFilters?: boolean;
};

export const PipelinesList = memo(
  ({ pipelines, mode = 'standard', showDescription, withFilters }: Props) => {
    const { filteredItems, onSearchChange, filtersProps, search } =
      useNgsFilters({
        items: pipelines,
        withFilters,
        filtersToDisplay: pipelinesFiltersToDisplay,
      });

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
        items={filteredItems}
        render={renderItem}
        sliced
        virtualized
        search={search}
        afterSearch={
          filtersProps && (
            <NgsFilters className="flex-shrink-0 flex-wrap" {...filtersProps} />
          )
        }
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
