import type { Project } from '@cloud-pipeline/core';
import { useMemo } from 'react';
import type { Tag, TagFilters } from '../types';
import { ProjectFilter } from '../constants';

type Props = {
  project: Project;
  tagsToFilter: TagFilters;
  tagToExclude?: string;
  tagToReplace?: TagFilters;
};

export const doesProjectMatchFilters = ({
  project,
  tagsToFilter,
  tagToReplace = {},
}: Props) => {
  if (!Object.keys(tagsToFilter).length) {
    return true;
  }

  const effectiveFilters = Object.entries({
    ...tagsToFilter,
    ...tagToReplace,
  });

  return effectiveFilters.every(([filterName, values]) => {
    if (filterName === (ProjectFilter.OWNER as string)) {
      return values.includes(project.owner);
    }

    const projectTagValue = project.data?.[filterName]?.value;
    return projectTagValue && values.includes(projectTagValue);
  });
};

const buildTagsMap = (projects?: Project[]) => {
  if (!projects?.length) {
    return {};
  }

  // Map for faster lookup
  const tagsMap: Record<string, Map<string, Tag>> = {};

  for (const project of projects) {
    if (!project.data) {
      continue;
    }

    for (const [key, { value }] of Object.entries(project.data)) {
      if (!tagsMap[key]) {
        tagsMap[key] = new Map();
      }

      const currentTagMap = tagsMap[key];
      const tag = currentTagMap.get(value);

      if (tag) {
        tag.count++;
      } else {
        currentTagMap.set(value, { id: value, count: 1 });
      }
    }
  }

  // Convert maps to arrays
  const tags: Record<string, Tag[]> = {};
  for (const [key, tagMap] of Object.entries(tagsMap)) {
    tags[key] = Array.from(tagMap.values());
  }

  return tags;
};

export const useProjectTags = (
  tagsToFilter: TagFilters,
  projects?: Project[],
) => {
  const projectTags = useMemo(() => {
    return buildTagsMap(projects);
  }, [projects]);
  console.log('🚀 ~ projectTags ~ projectTags:', projectTags);

  const dynamicTags = useMemo(() => {
    if (!Object.keys(tagsToFilter).length) {
      return projectTags;
    }

    const updatedTags: Record<string, Tag[]> = {};

    Object.entries(projectTags).forEach(([tagName, tagValues]) => {
      updatedTags[tagName] = tagValues.map((tag) => {
        const matchingProjects = projects?.filter((project) =>
          doesProjectMatchFilters({
            project,
            tagsToFilter,
            tagToReplace: { [tagName]: [tag.id] },
          }),
        );

        const testProjects = matchingProjects?.filter(
          (project) => project?.data?.[tagName]?.value === tag.id,
        );

        return {
          ...tag,
          count: testProjects?.length ?? 0,
        };
      });
    });

    return updatedTags;
  }, [projectTags, projects, tagsToFilter]);

  return dynamicTags;
};
