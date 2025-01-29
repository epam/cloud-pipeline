import { Project } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export type RegisterProjectOptions = {
  parentFolderId?: number;
  abortSignal?: AbortSignal;
};

export async function registerProject(
  name: string,
  options?: RegisterProjectOptions,
): Promise<Project> {
  const { parentFolderId, abortSignal } = options ?? {};
  return await cloudPipelineApi.jsonPost<Project>({
    uri: 'folder/register?templateName=Project',
    body: {
      name,
      parentId: parentFolderId,
    },
    signal: abortSignal,
  });
}
