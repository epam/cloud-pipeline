import { DataStoragePageResponse } from '@cloud-pipeline/core';
import cloudPipelineApi from '../cloud-pipeline-api';

export const DEFAULT_DATASTORAGE_PAGE_SIZE = 20;

type DatastoragePageParameters = {
  id: number;
  path: string | undefined;
  showVersion?: boolean;
  showArchived?: boolean;
  pageSize?: number;
  marker: string | undefined;
};

export async function fetchDataStoragePage({
  id,
  path,
  showVersion = false,
  showArchived = false,
  pageSize = DEFAULT_DATASTORAGE_PAGE_SIZE,
  marker,
}: DatastoragePageParameters): Promise<DataStoragePageResponse> {
  const query = [
    path !== undefined ? `path=${encodeURIComponent(path)}` : null,
    `showVersion=${showVersion}`,
    `showArchived=${showArchived}`,
    `pageSize=${pageSize}`,
    marker !== undefined ? `marker=${encodeURIComponent(marker)}` : null,
  ]
    .filter(Boolean)
    .join('&');
  const response = await cloudPipelineApi.jsonGet<DataStoragePageResponse>({
    uri: `/datastorage/${id}/list/page?${query}`,
  });
  return response;
}
