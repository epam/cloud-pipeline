import type { Pipeline, Project, UserInfo } from '@cloud-pipeline/core';
import { useMemo } from 'react';
import type { FilterToDisplay, NgsTags, TagFilters } from '../types';
import { collectNgsTags } from '../helpers';

type Props = {
  tagsToFilter: TagFilters;
  isMatchingFilters: (
    item: Project | Pipeline,
    overrideFilters?: TagFilters,
  ) => boolean;
  users?: UserInfo[];
  items?: (Project | Pipeline)[];
  searchedItems?: (Project | Pipeline)[];
  filtersToDisplay: FilterToDisplay[];
};

export const useNgsTags = ({
  isMatchingFilters,
  tagsToFilter,
  filtersToDisplay,
  users = [],
  items = [],
  searchedItems = [],
}: Props) => {
  const initialTags = useMemo(() => {
    if (!items.length) {
      return {};
    }

    return collectNgsTags({
      filtersToDisplay,
      items,
      users,
    });
  }, [filtersToDisplay, items, users]);

  const dynamicTags = useMemo(() => {
    if (!Object.keys(tagsToFilter).length) {
      return initialTags;
    }

    const updatedTags: NgsTags = { ...initialTags };

    Object.entries(initialTags).forEach(([tagName, tagValues]) => {
      updatedTags[tagName] = tagValues.map((tag) => {
        const matchingItems = searchedItems.filter((item) =>
          isMatchingFilters(item, { [tagName]: [tag.id] }),
        );

        return {
          ...tag,
          count: matchingItems.length,
        };
      });
    });

    return updatedTags;
  }, [tagsToFilter, initialTags, searchedItems, isMatchingFilters]);

  return dynamicTags;
};
