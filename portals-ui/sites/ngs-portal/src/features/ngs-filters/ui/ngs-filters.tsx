import { useCallback, useEffect } from 'react';
import { NgsFilter } from '../../../shared/constants/filters';
import { SelectFilter } from '../../../shared/ui';
import type { FilterToDisplay, NgsItem } from '../types';
import { useNgsDynamicFilters } from '../hooks';
import { CommonProps } from '@cloud-pipeline/components';
import classNames from 'classnames';

type Props<T extends NgsItem> = CommonProps & {
  onFilteredItemsChange: (items: T[]) => void;
  items?: T[];
  searchedItems?: T[];
  filtersToDisplay: FilterToDisplay[];
};

export const NgsFilters = <T extends NgsItem>({
  className,
  style,
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
  } = useNgsDynamicFilters({
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
    <div
      className={classNames(className, 'flex flex-wrap gap-2')}
      style={style}>
      {Object.entries(tags).map(([key, { label, values }]) => {
        const options =
          values?.map((tag) => ({
            value: tag.id,
            label:
              tag.count !== undefined ? `${tag.id} (${tag.count})` : tag.id,
            disabled: !tag.count && !tagsToFilter[key]?.includes(tag.id),
          })) || [];

        return (
          <SelectFilter
            key={key}
            options={options}
            selectedValues={tagsToFilter[key] ?? []}
            onChange={handleFilterChange(key)}
            label={label}
            onFocus={() => handleFocus(key)}
          />
        );
      })}
    </div>
  );
};
