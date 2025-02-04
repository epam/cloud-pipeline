import { useEffect, useMemo, useState } from 'react';
import type {
  NgsItem,
  NgsItemsFiltersOptions,
  NgsItemsFiltersActions,
  NgsItemsTagFilters,
} from '../types.ts';
import { useUsers } from '../../../state/users-info/hooks.ts';
import type { NgsItemsFiltersProcessorState } from '../helpers/ngs-items-filters-processor.ts';
import { NgsItemsFiltersProcessor } from '../helpers/ngs-items-filters-processor.ts';

export function useFilteredNgsItems<T extends NgsItem>(
  items: T[] | undefined,
  options?: NgsItemsFiltersOptions<T>,
): NgsItemsFiltersProcessorState<T> & NgsItemsFiltersActions {
  const { taggedObjectSettings, searchCallback, filtersEnabled } =
    options ?? {};
  const [search, onSearchChanged] = useState<string | undefined>(undefined);
  const [filters, onFiltersChanged] = useState<NgsItemsTagFilters | undefined>(
    undefined,
  );
  const [processor, setProcessor] = useState<
    NgsItemsFiltersProcessor<T> | undefined
  >();
  const users = useUsers();
  const [state, setState] = useState<NgsItemsFiltersProcessorState<T>>({
    pending: true,
    error: undefined,
    search,
    filters,
    filteredItems: [],
    config: [],
  });
  useEffect(() => {
    const aProcessor = new NgsItemsFiltersProcessor({
      items: [],
      taggedObjectSettings,
      searchCallback,
      listener: setState,
      filtersEnabled,
    });
    setProcessor(aProcessor);
    return () => {
      setProcessor(undefined);
      aProcessor.destroy();
    };
  }, [
    searchCallback,
    taggedObjectSettings,
    filtersEnabled,
    setProcessor,
    setState,
  ]);
  useEffect(() => {
    if (processor) {
      processor.setPayload({ items: items ?? [], users, search, filters });
    }
  }, [processor, items, users, search, filters]);
  const { pending, error, config, filteredItems } = state;
  return useMemo(
    () => ({
      pending,
      error,
      config,
      filteredItems,
      search,
      filters,
      onSearchChanged,
      onFiltersChanged,
    }),
    [
      pending,
      error,
      config,
      filteredItems,
      search,
      filters,
      onSearchChanged,
      onFiltersChanged,
    ],
  );
}
