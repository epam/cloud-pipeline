import { Project } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

const parentIdMock = 3; // UI-ZONE

export async function registerProject(name: string): Promise<Project> {
  const result = await cloudPipelineApi.jsonPost<Project>({
    uri: 'folder/register?templateName=Project',
    body: {
      name,
      parentId: parentIdMock,
    },
  });
  return result;
}
