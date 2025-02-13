import { DataStorageItemTypes, Project } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export type UpdateDataStorageItemPayload = {
  action: string;
  path: string;
  type: DataStorageItemTypes;
  contents?: string[];
  oldPath?: string;
  version?: string;
};

export async function updateDataStorageItem(
  storageId: number,
  payload: UpdateDataStorageItemPayload[],
  abortSignal?: AbortSignal,
): Promise<Project> {
  return await cloudPipelineApi.jsonPost<Project>({
    uri: `/datastorage/${storageId}/list`,
    body: payload,
    signal: abortSignal,
  });
}
