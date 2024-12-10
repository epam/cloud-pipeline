import { useEffect, useMemo } from 'react';
import { Spinner } from '@epam/uui';
import { loadPipelines } from '../../state/pipelines/load-pipelines';
import { usePipelinesState } from '../../state/pipelines/hooks';
import { PipelinesList } from '../home/components/pipelines-list.tsx';
import { useSearch } from '../../shared/hooks/use-search.ts';
import { useNgsFilters } from '../projects/hooks/use-ngs-filters.ts';
import { useNgsTags } from '../projects/hooks/use-ngs-tags.ts';
import { NgsFilters } from '../projects/components/ngs-filters';
import { pipelinesFiltersToDisplay } from './constants.ts';

export function PipelinesPage() {
  const { pipelines, error, pending } = usePipelinesState();

  const {
    search,
    onSearchChange,
    filtered: searchedPipelines,
  } = useSearch({
    items: pipelines ?? [],
  });

  const {
    tagsToFilter,
    usersInfo,
    isMatchingFilters,
    handleFilterValueChange,
    handleOwnersFilterFocus,
  } = useNgsFilters();

  const pipelineTags = useNgsTags({
    tagsToFilter,
    isMatchingFilters,
    items: pipelines,
    users: usersInfo,
    searchedItems: searchedPipelines,
    filtersToDisplay: pipelinesFiltersToDisplay,
  });
  console.log('🚀 ~ PipelinesPage ~ pipelineTags:', pipelineTags);

  const filteredPipelines = useMemo(
    () => searchedPipelines.filter((pipeline) => isMatchingFilters(pipeline)),
    [isMatchingFilters, searchedPipelines],
  );

  useEffect(() => {
    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);

  if (error) {
    return <div>{error}</div>;
  }

  if (pending) {
    return <Spinner />;
  }

  if (!pipelines) {
    return <div>No data</div>;
  }

  return (
    <PipelinesList
      pipelines={filteredPipelines}
      mode="extended"
      filters={
        <NgsFilters
          filtersToDisplay={pipelinesFiltersToDisplay}
          tags={pipelineTags}
          onFilterValueChange={handleFilterValueChange}
          tagsToFilter={tagsToFilter}
          onOwnersFilterFocus={handleOwnersFilterFocus}
        />
      }
    />
  );
}
