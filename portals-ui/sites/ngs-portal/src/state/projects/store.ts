import type { ProjectsStore } from './types.ts';
import createLoadableStore from '../common/loadable-store/create-loadable-store.ts';
import type { Project } from '@cloud-pipeline/core';
import { fetchNgsProjects } from './fetch-ngs-projects.ts';

const projectsStore = createLoadableStore<ProjectsStore>(
  fetchNgsProjects,
  [],
  (_, get) => ({
    getProjectById(projectId: number): Project | undefined {
      const { data: projects } = get();
      return projects?.find((project) => project.id === projectId);
    },
  }),
);

export { projectsStore };
