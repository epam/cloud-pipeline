import {
  MetadataClass,
  MetadataEntity,
  MetadataEntityData,
  MetadataEntityRef,
  MetadataFilter,
  MetadataKeysBulkUpdateRequest,
  MetadataKeyUpdate,
  MetadataLoadResponseItem,
} from '../../@types/metadata.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function loadMetadata(
  entities: MetadataEntityRef[],
): Promise<MetadataLoadResponseItem[]> {
  if (!entities.length) {
    return [];
  }
  const result = await cloudPipelineApi.jsonPost<MetadataLoadResponseItem[] | undefined>({
    uri: 'metadata/load',
    body: entities,
  });
  return result ?? [];
}

export async function loadMetadataForFolderContents(
  folderId?: number,
): Promise<MetadataLoadResponseItem[]> {
  const result = await cloudPipelineApi.jsonGet<MetadataLoadResponseItem[] | undefined>({
    uri: 'metadata/folder',
    query: {parentFolderId: folderId},
  });
  return result ?? [];
}

export async function loadEntityMetadata(
  entityId: number,
  entityClass: string,
): Promise<MetadataLoadResponseItem> {
  const metadata = await loadMetadata([{entityId, entityClass}]);
  return metadata[0] ?? {};
}

export async function updateMetadata(request: Record<string, unknown>): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: 'metadata/update', body: request});
}

export async function updateMetadataKey(request: MetadataKeyUpdate): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: 'metadata/updateKey', body: request});
}

export async function updateMetadataKeys(request: MetadataKeysBulkUpdateRequest): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: 'metadata/updateKeys', body: request});
}

export async function deleteMetadataKeys(request: MetadataKeysBulkUpdateRequest): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: 'metadata/deleteKeys', body: request});
}

export async function loadMetadataKeys(entity: MetadataEntityRef): Promise<string[]> {
  return cloudPipelineApi.jsonGet<string[]>({uri: 'metadata/keys', query: entity});
}

export async function findMetadata(
  query: Record<string, unknown>,
): Promise<MetadataLoadResponseItem[]> {
  return cloudPipelineApi.jsonGet<MetadataLoadResponseItem[]>({uri: 'metadata/find', query});
}

export async function deleteMetadata(request: MetadataEntityRef): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: 'metadata/delete', body: request});
}

export async function deleteMetadataKey(request: MetadataKeyUpdate): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: 'metadata/deleteKey', body: request});
}

export async function loadAllMetadataClasses(): Promise<MetadataClass[]> {
  return cloudPipelineApi.jsonGet<MetadataClass[]>({uri: 'metadataClass/loadAll'});
}

export async function registerMetadataClass(metadataClass: MetadataClass): Promise<MetadataClass> {
  return cloudPipelineApi.jsonPost<MetadataClass>({
    uri: 'metadataClass/register',
    body: metadataClass,
  });
}

export async function deleteMetadataClass(id: number): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: `metadataClass/${id}/delete`});
}

export async function loadMetadataEntity(id: number): Promise<MetadataEntity> {
  return cloudPipelineApi.jsonGet<MetadataEntity>({uri: `metadataEntity/${id}/load`});
}

export async function filterMetadataEntities(filter: MetadataFilter): Promise<MetadataEntity[]> {
  return cloudPipelineApi.jsonPost<MetadataEntity[]>({uri: 'metadataEntity/filter', body: filter});
}

export async function saveMetadataEntity(entity: MetadataEntity): Promise<MetadataEntity> {
  return cloudPipelineApi.jsonPost<MetadataEntity>({uri: 'metadataEntity/save', body: entity});
}

export async function deleteMetadataEntity(id: number): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: `metadataEntity/${id}/delete`});
}

export async function loadMetadataEntityKeys(id: number): Promise<string[]> {
  return cloudPipelineApi.jsonGet<string[]>({uri: 'metadataEntity/keys', query: {id}});
}

export async function loadMetadataEntityFields(
  className: string,
): Promise<Record<string, unknown>> {
  return cloudPipelineApi.jsonGet({uri: 'metadataEntity/fields', query: {className}});
}
