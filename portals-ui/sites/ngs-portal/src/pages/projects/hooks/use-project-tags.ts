import type { Project } from '@cloud-pipeline/core';
import { useMemo } from 'react';

export const useProjectTags = (projects: Project[]) => {
  return useMemo(() => {
    if (!projects) {
      return {};
    }

    const tags: Record<string, Set<string>> = {};

    for (const project of projects) {
      if (project.data) {
        for (const [key, { value }] of Object.entries(project.data)) {
          if (!tags[key]) {
            tags[key] = new Set();
          }

          tags[key].add(value);
        }
      }
    }

    return Object.fromEntries(
      Object.entries(tags).map(([key, values]) => [key, Array.from(values)]),
    );
  }, [projects]);
};
