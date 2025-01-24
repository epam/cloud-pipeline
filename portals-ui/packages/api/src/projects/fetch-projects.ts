import { Project, ProjectsResponse } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchProjects(
  abortSignal?: AbortSignal,
): Promise<Project[]> {
  const result = await cloudPipelineApi.jsonGet<ProjectsResponse>({
    uri: 'folder/projects',
    signal: abortSignal,
  });
  return result?.childFolders ?? [];
}
