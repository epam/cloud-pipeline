import type { Project } from '@cloud-pipeline/core';
import { ProjectFilter } from '../constants';
import type { TagFilters } from '../types';

type Props = {
  project: Project;
  tagsToFilter: TagFilters;
  tagToExclude?: string;
  tagToReplace?: TagFilters;
};

export const doesProjectMatchFilters = ({
  project,
  tagsToFilter,
  tagToExclude,
  tagToReplace = {},
}: Props) => {
  if (Object.keys(tagsToFilter).length === 0) {
    return true;
  }

  const filterTags = {
    ...tagsToFilter,
    ...tagToReplace,
  };

  const matchesTags = Object.entries(filterTags)
    .filter(([tagName]) => tagToExclude !== tagName)
    .every(([filterName, values]) => {
      if (filterName === (ProjectFilter.OWNER as string)) {
        return values.includes(project.owner);
      }

      const projectTagValue = project.data?.[filterName]?.value;
      return projectTagValue && values.includes(projectTagValue);
    });

  return matchesTags;
};
