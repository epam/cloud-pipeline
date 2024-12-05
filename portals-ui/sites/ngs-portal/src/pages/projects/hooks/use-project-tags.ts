import type { Project } from '@cloud-pipeline/core';
import { useMemo } from 'react';
import type { Tag } from '../types';

export const useProjectTags = (projects?: Project[]) => {
  return useMemo(() => {
    if (!projects?.length) {
      return {};
    }

    // Map for faster lookup
    const tagsMap: Record<string, Map<string, Tag>> = {};

    for (const { data } of projects) {
      if (!data) {
        continue;
      }

      for (const [key, { value }] of Object.entries(data)) {
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
  }, [projects]);
};
