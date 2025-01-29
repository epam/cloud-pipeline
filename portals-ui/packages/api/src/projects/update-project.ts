import { Project } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

type ProjectInfo = Pick<Project, 'id' | 'name' | 'parentId' | 'description'>;

export async function updateProject(
  projectInfo: ProjectInfo,
): Promise<Project> {
  const result = await cloudPipelineApi.jsonPost<Project>({
    uri: 'folder/update',
    body: projectInfo,
  });
  return result;
}
