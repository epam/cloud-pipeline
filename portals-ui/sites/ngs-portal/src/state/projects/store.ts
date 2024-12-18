import { createStore } from 'zustand';
import type { ProjectsState, ProjectsStore } from './types.ts';

const projectsStore = createStore<ProjectsStore>((set, get) => ({
  projects: undefined,
  error: undefined,
  pending: false,
  setProjects(result: Pick<ProjectsState, 'projects' | 'error'>) {
    const { projects, error } = result;
    set({ projects, error, pending: false });
  },
  setError(error: string | undefined) {
    set({ error });
  },
  setPending(pending: boolean) {
    set({ pending });
  },
  getProjectById(projectId: number) {
    const { projects } = get();
    return projects?.find((project) => project.id === projectId);
  },
}));

export { projectsStore };
