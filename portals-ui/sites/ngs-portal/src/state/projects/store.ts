import type { ProjectsStore } from './types.ts';
import createLoadableStore from '../common/loadable-store/create-loadable-store.ts';
import { fetchProjects } from '@cloud-pipeline/api';
import type { Project } from '@cloud-pipeline/core';

const projectsStore = createLoadableStore<ProjectsStore>(
  fetchProjects,
  [],
  (_, get) => ({
    getProjectById(projectId: number): Project | undefined {
      const { data: projects } = get();
      return projects?.find((project) => project.id === projectId);
    },
  }),
);

export { projectsStore };
