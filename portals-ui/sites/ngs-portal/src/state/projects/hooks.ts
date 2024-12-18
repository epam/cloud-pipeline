import type { ProjectsState, ProjectsStore } from './types.ts';
import { useStore } from 'zustand';
import { projectsStore } from './store.ts';
import { useMemo } from 'react';

export function useProjectsStore(): ProjectsStore {
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
