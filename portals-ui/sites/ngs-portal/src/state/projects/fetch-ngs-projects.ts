import type { Project } from '@cloud-pipeline/core';
import fetchSettings from '../../shared/settings/fetch-settings.ts';
import { fetchProjects } from '@cloud-pipeline/api';
import {flattenNumberIdentifiers} from "../../shared/helpers/arrays.ts";

export async function fetchNgsProjects(
  abortSignal?: AbortSignal,
): Promise<Project[]> {
  const { ngsProjectsRoot } = await fetchSettings();
  const ids = flattenNumberIdentifiers(ngsProjectsRoot);
  if (ids.length === 0) {
    return fetchProjects(abortSignal);
  }
  const allProjects = await fetchProjects();
  const rootsSet = new Set<number>(ids);
  return allProjects
    .filter((project) => project.parentId && rootsSet.has(project.parentId))
    .map((project: Project) => ({
      ...project,
      childFolders: [],
      pipelines: [],
      storages: [],
      configurations: [],
    }));
}
