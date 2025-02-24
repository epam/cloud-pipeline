import { DataStorageItem, DataStorageItemTypes, Project } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

function mapDataStorageItemPayload(item: DataStorageItem): { path: string; type: DataStorageItemTypes } {
  const { path, type } = item;
  return {
    path,
    type,
  };
}

export async function deleteDataStorageItems(
  storageId: number,
  items: DataStorageItem[],
  abortSignal?: AbortSignal,
): Promise<Project> {
  return await cloudPipelineApi.jsonDelete<Project>({
    uri: `/datastorage/${storageId}/list`,
    body: items.map(mapDataStorageItemPayload),
    signal: abortSignal,
  });
}

export async function deleteDataStorageItem(
  storageId: number,
  item: DataStorageItem,
  abortSignal?: AbortSignal,
): Promise<Project> {
  return await deleteDataStorageItems(storageId, [item], abortSignal);
}
