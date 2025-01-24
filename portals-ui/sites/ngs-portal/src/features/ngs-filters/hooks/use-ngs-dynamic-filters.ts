import { useCallback, useMemo, useState } from 'react';
import type { FilterToDisplay, NgsItem, NgsTags, TagFilters } from '../types';
import { useUsers } from '../../../state/users-info/hooks';
import { NgsFilter } from '../../../shared/constants/filters';
import { collectNgsTags } from '../helpers';

type Props<T extends NgsItem> = {
  items: T[];
  searchedItems: T[];
  filtersToDisplay: FilterToDisplay[];
};

export const useNgsDynamicFilters = <T extends NgsItem>({
  filtersToDisplay,
  items,
  searchedItems,
}: Props<T>) => {
  const [tagsToFilter, setTagsToFilter] = useState<TagFilters>({});
  const users = useUsers();

  const isMatchingFilters = useCallback(
    <T extends NgsItem>(item: T, overrideFilters: TagFilters = {}) => {
      const effectiveFilters = Object.entries({
        ...tagsToFilter,
        ...overrideFilters,
      });

      return effectiveFilters.every(([filterName, values]) => {
        if (filterName === (NgsFilter.OWNER as string)) {
          return values.includes(item.owner);
        }

        const tagValue = item.data?.[filterName]?.value;
        return tagValue && values.includes(tagValue);
      });
    },
    [tagsToFilter],
  );

  const handleFilterValueChange = useCallback(
    (tagName: string, selectedItems?: string[]) => {
      setTagsToFilter((prevTags) => {
        if (!selectedItems?.length) {
          const newTags = { ...prevTags };
          delete newTags[tagName];
          return newTags;
        }

        return { ...prevTags, [tagName]: selectedItems };
      });
    },
    [],
  );

  const initialTags = useMemo(() => {
    if (!items?.length) {
      return {};
    }

    return collectNgsTags({
      filtersToDisplay,
      items,
      users: users,
    });
  }, [filtersToDisplay, items, users]);

  const dynamicTags = useMemo(() => {
    const updatedTags: NgsTags = { ...initialTags };

    Object.entries(initialTags).forEach(([tagName, { values }]) => {
      const updatedValues = values.map((tag) => {
        const matchingItems = searchedItems.filter((item) =>
          isMatchingFilters(item, { [tagName]: [tag.id] }),
        );

        return {
          ...tag,
          count: matchingItems.length,
        };
      });

      updatedTags[tagName].values = updatedValues;
    });

    return updatedTags;
  }, [initialTags, searchedItems, isMatchingFilters]);

  const filteredItems = useMemo(
    () => searchedItems.filter((project) => isMatchingFilters(project)),
    [isMatchingFilters, searchedItems],
  );

  return useMemo(
    () => ({
      handleFilterValueChange,
      tagsToFilter,
      filteredItems,
      tags: dynamicTags,
    }),
    [handleFilterValueChange, tagsToFilter, filteredItems, dynamicTags],
  );
};
