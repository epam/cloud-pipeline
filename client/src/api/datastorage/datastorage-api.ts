import type {
  DataStorage,
  DataStorageDownloadFileUrl,
  DataStorageItem,
  DataStorageItemContent,
  DataStorageListing,
  DataStorageListingFilter,
  DataStorageTagSearchResult,
  DataStorageVO,
  GenerateDownloadUrlRequest,
  UpdateDataStorageItem,
} from '../../@types/datastorage.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function loadAllDataStorages(): Promise<DataStorage[]> {
  return cloudPipelineApi.jsonGet<DataStorage[]>({uri: 'datastorage/loadAll'});
}

export async function loadDataStorage(id: number): Promise<DataStorage> {
  return cloudPipelineApi.jsonGet<DataStorage>({uri: `datastorage/${id}/load`});
}

export async function findDataStorage(identifier: string): Promise<DataStorage> {
  return cloudPipelineApi.jsonGet<DataStorage>({uri: 'datastorage/find', query: {id: identifier}});
}

export async function findDataStorageByPath(path: string): Promise<DataStorage> {
  return cloudPipelineApi.jsonGet<DataStorage>({uri: 'datastorage/findByPath', query: {path}});
}

export async function findAllDataStoragesByPath(path: string): Promise<DataStorage[]> {
  return cloudPipelineApi.jsonGet<DataStorage[]>({uri: 'datastorage/findAllByPath', query: {path}});
}

export async function loadAvailableDataStorages(): Promise<DataStorage[]> {
  return cloudPipelineApi.jsonGet<DataStorage[]>({uri: 'datastorage/available'});
}

export async function loadAvailableDataStoragesWithMounts(): Promise<unknown[]> {
  return cloudPipelineApi.jsonGet({uri: 'datastorage/availableWithMounts'});
}

export async function listDataStorageItems(
  id: number,
  path?: string,
  showVersion?: boolean,
  showArchived?: boolean,
): Promise<DataStorageListing> {
  return cloudPipelineApi.jsonGet<DataStorageListing>({
    uri: `datastorage/${id}/list`,
    query: {path, showVersion, showArchived},
  });
}

export async function listDataStorageItemsPage(
  id: number,
  query: {path?: string; pageSize?: number; marker?: string; showVersion?: boolean},
): Promise<DataStorageListing> {
  return cloudPipelineApi.jsonGet<DataStorageListing>({
    uri: `datastorage/${id}/list/page`,
    query,
  });
}

export async function filterDataStorageItems(
  id: number,
  filter: DataStorageListingFilter,
): Promise<DataStorageListing> {
  return cloudPipelineApi.jsonPost<DataStorageListing>({
    uri: `datastorage/${id}/list/filter`,
    body: filter,
  });
}

export async function createDataStorageItems(
  id: number,
  items: UpdateDataStorageItem[],
): Promise<DataStorageItem[]> {
  return cloudPipelineApi.jsonPost<DataStorageItem[]>({
    uri: `datastorage/${id}/list`,
    body: items,
  });
}

export async function deleteDataStorageItems(
  id: number,
  items: UpdateDataStorageItem[],
): Promise<void> {
  await cloudPipelineApi.jsonDelete({
    uri: `datastorage/${id}/list`,
    body: items,
  });
}

export async function loadDataStorageContent(
  id: number,
  path: string,
  version?: string,
): Promise<DataStorageItemContent> {
  return cloudPipelineApi.jsonGet<DataStorageItemContent>({
    uri: `datastorage/${id}/content`,
    query: {path, version},
  });
}

export async function saveDataStorageContent(
  id: number,
  path: string,
  content: DataStorageItemContent,
): Promise<void> {
  await cloudPipelineApi.jsonPost({
    uri: `datastorage/${id}/content`,
    query: {path},
    body: content,
  });
}

export async function generateDataStorageDownloadUrl(
  id: number,
  path: string,
  version?: string,
): Promise<DataStorageDownloadFileUrl> {
  return cloudPipelineApi.jsonGet<DataStorageDownloadFileUrl>({
    uri: `datastorage/${id}/generateUrl`,
    query: {path, version},
  });
}

export async function generateDataStorageDownloadUrls(
  id: number,
  request: GenerateDownloadUrlRequest,
): Promise<DataStorageDownloadFileUrl[]> {
  return cloudPipelineApi.jsonPost<DataStorageDownloadFileUrl[]>({
    uri: `datastorage/${id}/generateUrl`,
    body: request,
  });
}

export async function saveDataStorage(storage: DataStorageVO): Promise<DataStorage> {
  return cloudPipelineApi.jsonPost<DataStorage>({uri: 'datastorage/save', body: storage});
}

export async function updateDataStorage(storage: DataStorageVO): Promise<DataStorage> {
  return cloudPipelineApi.jsonPost<DataStorage>({uri: 'datastorage/update', body: storage});
}

export async function updateDataStoragePolicy(
  id: number,
  policy: DataStorageVO['storagePolicy'],
): Promise<DataStorage> {
  return cloudPipelineApi.jsonPost<DataStorage>({
    uri: 'datastorage/policy',
    body: {id, storagePolicy: policy},
  });
}

export async function deleteDataStorage(id: number): Promise<DataStorage> {
  return cloudPipelineApi.jsonDelete<DataStorage>({uri: `datastorage/${id}/delete`});
}

export async function deleteDataStorageWithOption(
  id: number,
  cloud: boolean,
): Promise<DataStorage> {
  return cloudPipelineApi.jsonDelete<DataStorage>({
    uri: `datastorage/${id}/delete`,
    query: {cloud},
  });
}

export async function loadDataStorageTags(
  id: number,
  path: string,
  version?: string,
): Promise<Record<string, string>> {
  return cloudPipelineApi.jsonGet<Record<string, string>>({
    uri: `datastorage/${id}/tags`,
    query: {path, version},
  });
}

export async function saveDataStorageTags(
  id: number,
  path: string,
  tags: Record<string, string>,
  version?: string,
  rewrite = true,
): Promise<void> {
  await cloudPipelineApi.jsonPost({
    uri: `datastorage/${id}/tags`,
    query: {path, version, ...(rewrite ? {rewrite: 'true'} : {})},
    body: tags,
  });
}

export async function deleteDataStorageTags(
  id: number,
  path: string,
  keys: string[],
  version?: string,
): Promise<void> {
  await cloudPipelineApi.jsonDelete({
    uri: `datastorage/${id}/tags`,
    query: {path, version},
    body: keys,
  });
}

export async function searchDataStorageTags(
  request: Record<string, unknown>,
): Promise<DataStorageTagSearchResult[]> {
  return cloudPipelineApi.jsonPost<DataStorageTagSearchResult[]>({
    uri: 'datastorage/tags/search',
    body: request,
  });
}

export async function filterDataStorages(filter: Record<string, unknown>): Promise<DataStorage[]> {
  return cloudPipelineApi.jsonPost<DataStorage[]>({uri: 'datastorage/filter', body: filter});
}

export async function requestDataStorageTempCredentials(
  request: Record<string, unknown>,
): Promise<unknown> {
  return cloudPipelineApi.jsonPost({uri: 'datastorage/tempCredentials/', body: request});
}

export async function registerDataStorageRule(rule: Record<string, unknown>): Promise<unknown> {
  return cloudPipelineApi.jsonPost({uri: 'datastorage/rule/register', body: rule});
}

export async function loadDataStorageRules(id: number): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: 'datastorage/rule/load', query: {id}});
}

export async function deleteDataStorageRule(id: number): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: 'datastorage/rule/delete', query: {id}});
}
