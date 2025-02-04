import { memo } from 'react';
import cn from 'classnames';
import type { Pipeline } from '@cloud-pipeline/core';
import { ItemsPanel } from '../items-panel';
import { PipelineCard } from '../cards';
import { ShareIcon } from '@heroicons/react/24/outline';
import { NgsFilters, useFilteredNgsItems } from '../ngs-filters';
import {
  usePipelinesState,
  useReloadPipelines,
} from '../../state/pipelines/hooks.ts';
import { useNgsPipelineSettings } from '../../state/settings/hooks.ts';
import './pipelines-list.css';

type Props = {
  mode?: 'standard' | 'extended';
  showDescription?: boolean;
  withFilters?: boolean;
};

export const PipelinesList = memo(
  ({ mode = 'standard', showDescription, withFilters }: Props) => {
    const { data: pipelines, error, pending } = usePipelinesState();
    useReloadPipelines();

    const settings = useNgsPipelineSettings();
    const {
      filteredItems: filteredPipelines,
      search,
      onSearchChanged,
      filters,
      onFiltersChanged,
      config,
    } = useFilteredNgsItems(pipelines, { taggedObjectSettings: settings });

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
        className="h-full list-container overflow-auto"
        title={
          <div className="min-h-6 fill-current flex items-center flex-nowrap gap-1">
            <ShareIcon className="w-5 h-5 -rotate-90" />
            <span>Pipelines</span>
          </div>
        }
        items={filteredPipelines}
        render={renderItem}
        sliced={mode !== 'extended'}
        virtualized
        search={search}
        afterSearch={
          withFilters && (
            <NgsFilters
              filters={filters}
              onFiltersChange={onFiltersChanged}
              config={config}
            />
          )
        }
        onSearchChange={onSearchChanged}
        itemKey="id"
        searchClassName={cn({
          'py-1': mode === 'extended',
        })}
        searchInputClassName="pipelines-list-search"
        viewAll={
          mode === 'extended'
            ? undefined
            : { title: 'View all pipelines', link: '/pipelines' }
        }
        isItemsLoading={pending}
        errorText={error && `Error: ${error}`}
      />
    );
  },
);
