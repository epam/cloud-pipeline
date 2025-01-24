import type { ProjectsStore } from './types.ts';
import { projectsStore } from './store.ts';
import {
  useLoadableStore,
  useRefreshLoadableStore,
} from '../common/loadable-store/hooks.ts';
import type { Project } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import { useEffect } from 'react';

export function useProjectsStore(): ProjectsStore {
  return useLoadableStore(projectsStore);
}

export function useProjects(): Project[] {
  return useProjectsStore().data;
}

export function useReloadProjectsFn(): () => Promise<Project[]> {
  return useRefreshLoadableStore(projectsStore);
}

export function useReloadProjects() {
  const fn = useReloadProjectsFn();
  useEffect(() => {
    fn().then(noop).catch(noop);
  }, [fn]);
}
