import { useCallback, useMemo, useState } from 'react';
import { ProjectFilter } from '../constants';
import type { Project } from '@cloud-pipeline/core';
import type { TagFilters } from '../types';

export const useProjectFilters = () => {
  const [tagsToFilter, setTagsToFilter] = useState<TagFilters>({});

  const doesProjectMatchFilters = useCallback(
    (project: Project) => {
      if (Object.keys(tagsToFilter).length === 0) {
        return true;
      }

      const hasOwnerFilter = ProjectFilter.OWNER in tagsToFilter;
      const hasOtherFilters = Object.keys(tagsToFilter).some(
        (key) => key !== (ProjectFilter.OWNER as string),
      );

      // Check if project matches owner filter (if present)
      const matchesOwner =
        !hasOwnerFilter ||
        tagsToFilter[ProjectFilter.OWNER]?.includes(project.owner);

      // Check if project matches tag filters (if present)
      const matchesTags =
        !hasOtherFilters ||
        Object.entries(tagsToFilter)
          .filter(([key]) => key !== (ProjectFilter.OWNER as string))
          .some(([filterName, values]) => {
            const projectTagValue = project.data?.[filterName]?.value;
            return projectTagValue && values.includes(projectTagValue);
          });

      return matchesOwner && matchesTags;
    },
    [tagsToFilter],
  );

  const handleFilterValueChange = useCallback(
    (tagName: string, selectedItems?: string[]) => {
      setTagsToFilter((prevTags) => {
        if (!selectedItems) {
          const newTags = { ...prevTags };
          delete newTags[tagName];
          return newTags;
        }

        return { ...prevTags, [tagName]: selectedItems };
      });
    },
    [],
  );

  return useMemo(
    () => ({
      handleFilterValueChange,
      doesProjectMatchFilters,
      tagsToFilter,
    }),
    [doesProjectMatchFilters, handleFilterValueChange, tagsToFilter],
  );
};
