import { useEffect, useState } from 'react';
import { Spinner } from '@epam/uui';
import { loadPipelines } from '../../state/pipelines/load-pipelines';
import { usePipelinesState } from '../../state/pipelines/hooks';
import { PipelinesList } from '../home/components/pipelines-list.tsx';
import { NgsFilters } from '../../features/ngs-filters';
import { pipelinesFiltersToDisplay } from './constants.ts';
import { useSearch } from '../../shared/hooks/use-search.ts';

export function PipelinesPage() {
  const { pipelines, error, pending } = usePipelinesState();

  useEffect(() => {
    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);

  const { filtered: searchedPipelines } = useSearch({
    items: pipelines ?? [],
  });

  const [filteredPipelines, setFilteredPipelines] = useState(searchedPipelines);

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
          items={pipelines}
          searchedItems={searchedPipelines}
          onFilteredItemsChange={setFilteredPipelines}
        />
      }
    />
  );
}
