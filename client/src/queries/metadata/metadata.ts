import {queryOptions} from '@tanstack/react-query';
import {loadEntityMetadata, loadMetadataForFolderContents} from '../../api';
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
