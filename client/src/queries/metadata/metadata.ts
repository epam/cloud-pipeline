import {queryOptions} from '@tanstack/react-query';
import {loadEntityMetadata, loadMetadataForFolderContents} from '../../api';
import {
  loadEntityTypesByFolder,
  loadAllMetadataClasses,
  loadEntityClassKeys,
} from '../../api/metadata/metadata-api.ts';
import {MetadataEntityRef} from '../../@types/metadata.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const metadataFolderKeys = {
  all: ['metadata-folder'] as const,
  details: () => [...metadataFolderKeys.all, 'detail'] as const,
  detail: (folderId: number | undefined) => [...metadataFolderKeys.details(), folderId] as const,
};

export const metadataEntityKeys = {
  all: ['metadata-entity'] as const,
  details: () => [...metadataEntityKeys.all, 'detail'] as const,
  detail: (entityId: number | undefined, entityClass: string | undefined) =>
    [...metadataEntityKeys.details(), entityId, entityClass] as const,
};

export function metadataFolderQueryOptions(
  folderId: number | undefined,
  opts?: QueryOptionsParams,
) {
  const {enabled = true, ...queryOpts} = opts ?? {};

  return queryOptions({
    ...queryOpts,
    queryKey: metadataFolderKeys.detail(folderId),
    queryFn: () => loadMetadataForFolderContents(folderId),
    enabled,
  });
}

export function metadataEntityQueryOptions(
  metadataEntity: MetadataEntityRef | undefined,
  opts?: QueryOptionsParams,
) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  const entityId = metadataEntity?.entityId;
  const entityClass = metadataEntity?.entityClass;

  return queryOptions({
    ...queryOpts,
    queryKey: metadataEntityKeys.detail(entityId, entityClass),
    queryFn: () => loadEntityMetadata(entityId as number, entityClass as string),
    enabled: enabled && entityId !== undefined && entityClass !== undefined,
  });
}

export function fetchMetadataFolder(folderId: number | undefined, opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(metadataFolderQueryOptions(folderId, opts));
}

export function fetchMetadataEntity(metadataEntity: MetadataEntityRef, opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(metadataEntityQueryOptions(metadataEntity, opts));
}

export const entityTypesByFolderKeys = {
  all: ['entity-types-by-folder'] as const,
  details: () => [...entityTypesByFolderKeys.all, 'detail'] as const,
  detail: (folderId: number) => [...entityTypesByFolderKeys.details(), folderId] as const,
};

export function entityTypesByFolderQueryOptions(
  folderId: number | undefined,
  opts?: QueryOptionsParams,
) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  return queryOptions({
    ...queryOpts,
    queryKey:
      folderId !== undefined
        ? entityTypesByFolderKeys.detail(folderId)
        : entityTypesByFolderKeys.all,
    queryFn: () => loadEntityTypesByFolder(folderId as number),
    enabled: enabled && folderId !== undefined,
  });
}

export const metadataClassKeys = {
  all: ['metadata-class'] as const,
};

export function metadataClassQueryOptions(opts?: QueryOptionsParams) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  return queryOptions({
    ...queryOpts,
    queryKey: metadataClassKeys.all,
    queryFn: loadAllMetadataClasses,
    enabled,
  });
}

export const entityClassKeysKeys = {
  all: ['entity-class-keys'] as const,
  details: () => [...entityClassKeysKeys.all, 'detail'] as const,
  detail: (folderId: number, metadataClass: string) =>
    [...entityClassKeysKeys.details(), folderId, metadataClass] as const,
};

export function entityClassKeysQueryOptions(
  folderId: number | undefined,
  metadataClass: string | undefined,
  opts?: QueryOptionsParams,
) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  return queryOptions({
    ...queryOpts,
    queryKey:
      folderId !== undefined && metadataClass !== undefined
        ? entityClassKeysKeys.detail(folderId, metadataClass)
        : entityClassKeysKeys.all,
    queryFn: () => loadEntityClassKeys(folderId!, metadataClass!),
    enabled: enabled && folderId !== undefined && !!metadataClass,
    placeholderData: [],
  });
}
