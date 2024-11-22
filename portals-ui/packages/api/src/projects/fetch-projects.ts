import { Project, ProjectsResponse } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchProjects(): Promise<Project[]> {
  const result = await cloudPipelineApi.jsonGet<ProjectsResponse>({
    uri: 'folder/projects',
  });
  return result?.childFolders ?? [];
}
