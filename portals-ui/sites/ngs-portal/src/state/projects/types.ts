import type { Project } from '@cloud-pipeline/core';
import type {
  LoadableStoreActions,
  LoadableStoreState,
} from '../common/loadable-store/types.ts';

export type ProjectsState = LoadableStoreState<Project[]>;

export type ProjectsActions = LoadableStoreActions<Project[]> & {
  getProjectById: (projectId: number) => Project | undefined;
};

export type ProjectsStore = ProjectsState & ProjectsActions;
