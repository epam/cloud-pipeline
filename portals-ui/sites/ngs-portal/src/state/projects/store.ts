import { createStore } from 'zustand';
import type { ProjectsState, ProjectsStore } from './types.ts';

const projectsStore = createStore<ProjectsStore>((set) => ({
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
}));

export { projectsStore };
