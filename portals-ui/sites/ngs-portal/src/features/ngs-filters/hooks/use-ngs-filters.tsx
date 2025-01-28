import { useState, useMemo, useCallback } from 'react';
import {
  useSearch,
  type SearchOptions,
} from '../../../shared/hooks/use-search';
import type { FilterToDisplay, NgsItem } from '../types';

type Props<T extends NgsItem> = {
  items: T[];
  withFilters?: boolean;
  filtersToDisplay: FilterToDisplay[];
  searchCallback?: (item: T, search: string) => boolean;
};

export const useNgsFilters = <T extends NgsItem>({
  items,
  withFilters,
  filtersToDisplay,
  searchCallback,
}: Props<T>) => {
  const {
    filtered: searchedItems,
    search,
    onSearchChange,
  } = useSearch<T>({
    items,
    searchCallback,
  } as SearchOptions<T>);

  const [filteredItems, setFilteredItems] = useState(searchedItems);

  const onFilteredItemsChange = useCallback((items: T[]) => {
    setFilteredItems(items);
  }, []);

  const filtersProps = useMemo(() => {
    if (!withFilters) {
      return null;
    }

    return {
      filtersToDisplay,
      items,
      searchedItems,
      onFilteredItemsChange,
    };
  }, [
    withFilters,
    filtersToDisplay,
    items,
    searchedItems,
    onFilteredItemsChange,
  ]);

  return useMemo(
    () => ({
      filtersProps,
      filteredItems: withFilters ? filteredItems : searchedItems,
      search,
      onSearchChange,
    }),
    [
      filteredItems,
      onSearchChange,
      filtersProps,
      search,
      searchedItems,
      withFilters,
    ],
  );
};
