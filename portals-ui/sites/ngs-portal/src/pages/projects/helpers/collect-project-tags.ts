import type { Project, UserInfo } from '@cloud-pipeline/core';
import { ProjectFilter, projectFiltersToDisplay } from '../constants';
import type { ProjectTags, Tag } from '../types';

export const collectProjectTags = (
  projects: Project[],
  users: UserInfo[],
): ProjectTags => {
  // Map for faster lookup
  const tagsMap: Record<string, Map<string, Tag>> = {};

  const isOwnerFilterAllowed =
    projectFiltersToDisplay.find(
      (tag) => tag.id === (ProjectFilter.OWNER as string),
    ) && users.length > 0;

  if (isOwnerFilterAllowed) {
    tagsMap[ProjectFilter.OWNER] = new Map(
      users.map((user) => [user.name, { id: user.name, count: 0 }]),
    );
  }

  const incrementTagCount = (key: string, value: string) => {
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
  };

  for (const { data, owner } of projects) {
    // setup owner tags
    if (isOwnerFilterAllowed) {
      incrementTagCount(ProjectFilter.OWNER, owner);
    }

    // setup the rest of tags
    if (!data) {
      continue;
    }

    for (const [key, { value }] of Object.entries(data)) {
      const isTagAllowed = projectFiltersToDisplay.find(
        (tag) => tag.id === key,
      );

      if (!isTagAllowed) {
        continue;
      }

      incrementTagCount(key, value);
    }
  }

  // Convert maps to arrays
  const tags: ProjectTags = {};
  for (const [key, tagMap] of Object.entries(tagsMap)) {
    tags[key] = Array.from(tagMap.values());
  }

  return tags;
};
