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

      const matchesTags = Object.entries(tagsToFilter).every(
        ([filterName, values]) => {
          if (filterName === (ProjectFilter.OWNER as string)) {
            return values.includes(project.owner);
          }

          const projectTagValue = project.data?.[filterName]?.value;
          return projectTagValue && values.includes(projectTagValue);
        },
      );

      return matchesTags;
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
