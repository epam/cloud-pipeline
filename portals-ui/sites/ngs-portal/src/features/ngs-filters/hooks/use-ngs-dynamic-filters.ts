import { useCallback, useMemo, useState } from 'react';
import { noop } from '@cloud-pipeline/core';
import type { FilterToDisplay, NgsItem, NgsTags, TagFilters } from '../types';
import { loadUsersInfo } from '../../../state/users-info/load-users-info';
import { useUsersInfoState } from '../../../state/users-info/hooks';
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
  const {
    usersInfo = [],
    pending: isUserInfoPending,
    loaded,
  } = useUsersInfoState();

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

  const handleOwnersFilterFocus = useCallback(() => {
    if (!usersInfo?.length && !isUserInfoPending && !loaded) {
      loadUsersInfo().then(noop).catch(noop);
    }
  }, [isUserInfoPending, loaded, usersInfo?.length]);

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
      users: usersInfo,
    });
  }, [filtersToDisplay, items, usersInfo]);

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
      handleOwnersFilterFocus,
      handleFilterValueChange,
      tagsToFilter,
      filteredItems,
      tags: dynamicTags,
    }),
    [
      handleOwnersFilterFocus,
      handleFilterValueChange,
      tagsToFilter,
      filteredItems,
      dynamicTags,
    ],
  );
};
