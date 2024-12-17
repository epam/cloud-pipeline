import type { ProjectsState, ProjectsStore } from './types.ts';
import { useStore } from 'zustand';
import { projectsStore } from './store.ts';
import { useMemo } from 'react';
import type { Project } from '@cloud-pipeline/core';

function useProjectsStore(): ProjectsStore {
  return useStore(projectsStore);
}

export function useProjectsState(): ProjectsState {
  const { projects, pending, error } = useProjectsStore();
  return useMemo(
    () => ({
      projects,
      pending,
      error,
    }),
    [projects, pending, error],
  );
}

export function useProject(
  projectId: string | number | undefined,
): Project | undefined {
  const { projects } = useProjectsState();
  return useMemo(() => {
    if (projectId !== undefined && projects) {
      return projects.find(
        (project) => String(project.id) === String(projectId),
      );
    }
    return undefined;
  }, [projectId, projects]);
}
