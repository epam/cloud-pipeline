import { useCallback, useEffect } from 'react';
import { NgsFilter } from '../../../shared/constants/filters';
import { SelectFilter } from '../../../shared/ui';
import type { FilterToDisplay, NgsItem } from '../types';
import { useNgsFilters } from '../hooks';

type Props<T extends NgsItem> = {
  onFilteredItemsChange: (items: T[]) => void;
  items?: T[];
  searchedItems?: T[];
  filtersToDisplay: FilterToDisplay[];
};

export const NgsFilters = <T extends NgsItem>({
  onFilteredItemsChange,
  items = [],
  searchedItems = [],
  filtersToDisplay,
}: Props<T>) => {
  const {
    tagsToFilter,
    filteredItems,
    tags,
    handleFilterValueChange,
    handleOwnersFilterFocus,
  } = useNgsFilters({
    items,
    filtersToDisplay,
    searchedItems,
  });

  useEffect(() => {
    onFilteredItemsChange(filteredItems);
  }, [filteredItems, onFilteredItemsChange]);

  const handleFilterChange = useCallback(
    (id: string) => (selectedItems?: string[]) => {
      handleFilterValueChange(id, selectedItems);
    },
    [handleFilterValueChange],
  );

  const handleFocus = useCallback(
    (id: string) => {
      if (id === (NgsFilter.OWNER as string)) {
        handleOwnersFilterFocus();
      }
    },
    [handleOwnersFilterFocus],
  );

  return (
    <div className="flex flex-wrap gap-2 min-w-[75%]">
      {Object.entries(tags).map(([key, { label, values }]) => {
        const options =
          values?.map((tag) => ({
            value: tag.id,
            label:
              tag.count !== undefined ? `${tag.id} (${tag.count})` : tag.id,
            disabled: !tag.count && !tagsToFilter[key]?.includes(tag.id),
          })) || [];

        return (
          // div is needed not to let the filter take 100% width
          // re-check if filter is not from uui library
          <div key={key}>
            <SelectFilter
              options={options}
              selectedValues={tagsToFilter[key] ?? []}
              onChange={handleFilterChange(key)}
              label={label}
              onFocus={() => handleFocus(key)}
            />
          </div>
        );
      })}
    </div>
  );
};
