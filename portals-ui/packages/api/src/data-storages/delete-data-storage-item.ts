import { DataStorageItemTypes, Project } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

type Payload = {
  path: string;
  type: DataStorageItemTypes;
};

export async function deleteDataStorageItem(
  storageId: number,
  payload: Payload,
  abortSignal?: AbortSignal,
): Promise<Project> {
  return await cloudPipelineApi.jsonDelete<Project>({
    uri: `/datastorage/${storageId}/list`,
    body: [payload],
    signal: abortSignal,
  });
}
