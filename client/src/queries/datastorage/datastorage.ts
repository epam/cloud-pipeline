import {queryOptions} from '@tanstack/react-query';
import {
  loadAllDataStorages,
  loadAvailableDataStorages,
  loadDataStorage,
  loadDataStorageContent,
  loadDataStorageTags,
  generateDataStorageDownloadUrl,
} from '../../api/datastorage/datastorage-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const storagesKeys = {
  all: ['storages'] as const,
};

export const availableStoragesKeys = {
  all: ['available-storages'] as const,
};

export const dataStorageKeys = {
  all: ['data-storage'] as const,
  details: () => [...dataStorageKeys.all, 'detail'] as const,
  detail: (id: number) => [...dataStorageKeys.details(), id] as const,
};

export const dataStorageContentKeys = {
  all: ['data-storage-content'] as const,
  detail: (id: number, path: string, version?: string) =>
    [...dataStorageContentKeys.all, id, version ?? '', path] as const,
};

export const dataStorageTagsKeys = {
  all: ['data-storage-tags'] as const,
  detail: (id: number, path: string, version?: string) =>
    [...dataStorageTagsKeys.all, id, version ?? '', path] as const,
};

export const dataStorageDownloadUrlKeys = {
  all: ['data-storage-download-url'] as const,
  detail: (id: number, path: string, version?: string) =>
    [...dataStorageDownloadUrlKeys.all, id, version ?? '', path] as const,
};

export function storagesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: storagesKeys.all,
    queryFn: loadAllDataStorages,
    placeholderData: [],
  });
}

export function availableStoragesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: availableStoragesKeys.all,
    queryFn: loadAvailableDataStorages,
    placeholderData: [],
  });
}

export function dataStorageQueryOptions(id: number | undefined, opts?: QueryOptionsParams) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  const queryKey = id !== undefined ? dataStorageKeys.detail(id) : dataStorageKeys.all;

  return queryOptions({
    ...queryOpts,
    queryKey,
    queryFn: () => loadDataStorage(id as number),
    enabled: enabled && id !== undefined,
  });
}

export function dataStorageContentQueryOptions(
  storageId: number | undefined,
  path: string | undefined,
  version?: string,
  opts?: QueryOptionsParams,
) {
  const {enabled = false, ...queryOpts} = opts ?? {};
  const canLoad = storageId !== undefined && !!path;

  return queryOptions({
    ...queryOpts,
    queryKey: dataStorageContentKeys.detail(storageId ?? 0, path ?? '', version),
    queryFn: () => loadDataStorageContent(storageId as number, path as string, version),
    enabled: enabled && canLoad,
  });
}

export function dataStorageTagsQueryOptions(
  storageId: number | undefined,
  path: string | undefined,
  version?: string,
  opts?: QueryOptionsParams,
) {
  const {enabled = false, ...queryOpts} = opts ?? {};
  const canLoad = storageId !== undefined && !!path;

  return queryOptions({
    ...queryOpts,
    queryKey: dataStorageTagsKeys.detail(storageId ?? 0, path ?? '', version),
    queryFn: () => loadDataStorageTags(storageId as number, path as string, version),
    enabled: enabled && canLoad,
  });
}

export function dataStorageDownloadUrlQueryOptions(
  storageId: number | undefined,
  path: string | undefined,
  version?: string,
  opts?: QueryOptionsParams,
) {
  const {enabled = false, ...queryOpts} = opts ?? {};
  const canLoad = storageId !== undefined && !!path;

  return queryOptions({
    ...queryOpts,
    queryKey: dataStorageDownloadUrlKeys.detail(storageId ?? 0, path ?? '', version),
    queryFn: () => generateDataStorageDownloadUrl(storageId as number, path as string, version),
    enabled: enabled && canLoad,
  });
}

export function fetchStorages(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(storagesQueryOptions(opts));
}

export function fetchDataStorage(id: number, opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(dataStorageQueryOptions(id, opts));
}
