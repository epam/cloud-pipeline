import { useCallback, useMemo, useState } from 'react';
import { ProjectFilter } from '../constants';
import type { Project } from '@cloud-pipeline/core';
import type { TagFilters } from '../types';

export const useProjectFilters = () => {
  const [tagsToFilter, setTagsToFilter] = useState<TagFilters>({});

  const isProjectMatchingFilters = useCallback(
    (project: Project, overrideFilters: TagFilters = {}) => {
      if (!Object.keys(tagsToFilter).length) {
        return true;
      }

      const effectiveFilters = Object.entries({
        ...tagsToFilter,
        ...overrideFilters,
      });

      return effectiveFilters.every(([filterName, values]) => {
        if (filterName === (ProjectFilter.OWNER as string)) {
          return values.includes(project.owner);
        }

        const projectTagValue = project.data?.[filterName]?.value;
        return projectTagValue && values.includes(projectTagValue);
      });
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
      isProjectMatchingFilters,
      tagsToFilter,
    }),
    [isProjectMatchingFilters, handleFilterValueChange, tagsToFilter],
  );
};
