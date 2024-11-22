import { fetchProjects } from '@cloud-pipeline/api';
import type { Project } from '@cloud-pipeline/core';
import { projectsStore } from './store.ts';

export async function loadProjects(): Promise<Project[]> {
  let projects: Project[] | undefined;
  let error: string | undefined;
  try {
    projectsStore.getState().setPending(true);
    projects = await fetchProjects();
    return projects;
  } catch (authError) {
    error = authError instanceof Error ? authError.message : `${authError}`;
    throw new Error(error);
  } finally {
    projectsStore.getState().setProjects({ projects, error });
  }
}
