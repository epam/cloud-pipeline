import type { Project } from '@cloud-pipeline/core';

export type ProjectsState = {
  projects: Project[] | undefined;
  error: string | undefined;
  pending: boolean;
};

export type ProjectsActions = {
  setError: (error: string | undefined) => void;
  setPending: (pending: boolean) => void;
  setProjects: (result: Pick<ProjectsState, 'projects' | 'error'>) => void;
  getProjectById: (projectId: number) => Project | undefined;
};

export type ProjectsStore = ProjectsState & ProjectsActions;
