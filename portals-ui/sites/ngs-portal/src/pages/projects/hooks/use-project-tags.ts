import type { Project, UserInfo } from '@cloud-pipeline/core';
import { useMemo } from 'react';
import type { ProjectTags, TagFilters } from '../types';
import { collectProjectTags } from '../helpers';

type Props = {
  tagsToFilter: TagFilters;
  isProjectMatchingFilters: (
    project: Project,
    overrideFilters?: TagFilters,
  ) => boolean;
  users?: UserInfo[];
  projects?: Project[];
};

export const useProjectTags = ({
  isProjectMatchingFilters,
  tagsToFilter,
  users = [],
  projects = [],
}: Props) => {
  const projectTags = useMemo(() => {
    if (!projects.length) {
      return {};
    }

    return collectProjectTags(projects, users);
  }, [projects, users]);

  const dynamicTags = useMemo(() => {
    if (!Object.keys(tagsToFilter).length || !projects.length) {
      return projectTags;
    }

    const updatedTags: ProjectTags = {};

    Object.entries(projectTags).forEach(([tagName, tagValues]) => {
      updatedTags[tagName] = tagValues.map((tag) => {
        const matchingProjects = projects.filter((project) =>
          isProjectMatchingFilters(project, { [tagName]: [tag.id] }),
        );

        return {
          ...tag,
          count: matchingProjects.length,
        };
      });
    });

    return updatedTags;
  }, [isProjectMatchingFilters, projectTags, projects, tagsToFilter]);

  return dynamicTags;
};
