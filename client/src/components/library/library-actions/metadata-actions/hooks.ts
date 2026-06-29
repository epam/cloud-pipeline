import {useCallback, useMemo} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQuery, useQueryClient} from '@tanstack/react-query';

import {folderKeys, libraryTreeKeys} from '../../../../queries';
import {
  entityTypesByFolderQueryOptions,
  metadataClassQueryOptions,
  entityClassKeysQueryOptions,
} from '../../../../queries/metadata/metadata.ts';
import {
  deleteMetadataEntitiesFromProject,
  saveMetadataEntity,
  type EntityTypeInfo,
} from '../../../../api/metadata/metadata-api.ts';
import type {MetadataEntityData} from '../../../../@types/metadata.ts';

function useMetadataActions(folderId: number | undefined, metadataClass?: string) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const {data: entityTypesByFolder = []} = useQuery(entityTypesByFolderQueryOptions(folderId));
  const {data: allMetadataClasses = []} = useQuery(metadataClassQueryOptions());
  const {data: entityClassKeys = []} = useQuery(
    entityClassKeysQueryOptions(folderId, metadataClass),
  );

  const entityTypes: EntityTypeInfo[] = useMemo(() => {
    const inFolder = new Set(entityTypesByFolder.map((e) => e.metadataClass.id));
    const rest = allMetadataClasses
      .filter(({id}) => !inFolder.has(id))
      .map((mc) => ({fields: [], metadataClass: {...mc, outOfProject: true}}));
    return [...entityTypesByFolder, ...rest];
  }, [entityTypesByFolder, allMetadataClasses]);

  const currentMetadataClassId = useMemo(
    () =>
      entityTypes
        .map((e) => e.metadataClass)
        .find((mc) => mc.name?.toLowerCase() === (metadataClass ?? '').toLowerCase())?.id,
    [entityTypes, metadataClass],
  );

  const ownKeyNames = useMemo(
    () => entityClassKeys.filter((k) => !k.predefined).map((k) => k.name),
    [entityClassKeys],
  );

  const currentEntityTypeFields = useMemo(() => {
    if (!metadataClass) return [];
    const et = entityTypes.find(
      (e) => e.metadataClass.name?.toLowerCase() === metadataClass.toLowerCase(),
    );
    return (et?.fields ?? []).filter((f) => ownKeyNames.includes(f.name));
  }, [entityTypes, metadataClass, ownKeyNames]);

  const pathFields = useMemo(
    () => currentEntityTypeFields.filter((f) => f.type.toLowerCase() === 'path').map((f) => f.name),
    [currentEntityTypeFields],
  );

  const nonPathFields = useMemo(
    () =>
      currentEntityTypeFields.filter((f) => f.type.toLowerCase() !== 'path').map((f) => f.name),
    [currentEntityTypeFields],
  );

  const invalidateAfterMutation = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
      folderId !== undefined
        ? queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)})
        : Promise.resolve(),
    ]);
  }, [queryClient, folderId]);

  const addInstance = useCallback(
    async (values: Record<string, unknown>) => {
      const classId = Number(values.entityClass);
      const metadataClassObj = entityTypes.map((e) => e.metadataClass).find((mc) => mc.id === classId);
      if (!metadataClassObj) throw new Error('Unknown metadata class');
      await saveMetadataEntity({
        entityId: 0,
        entityClass: metadataClassObj.name ?? '',
        classId,
        className: metadataClassObj.name ?? '',
        externalId: values.id as string | undefined,
        data: values.data as MetadataEntityData | undefined,
        parentId: folderId,
      });
      await invalidateAfterMutation();
    },
    [entityTypes, folderId, invalidateAfterMutation],
  );

  const deleteClass = useCallback(async () => {
    if (!metadataClass || folderId === undefined) return;
    await deleteMetadataEntitiesFromProject(folderId, metadataClass);
    await invalidateAfterMutation();
    navigate(`/folder/${folderId}/metadata`);
  }, [metadataClass, folderId, invalidateAfterMutation, navigate]);

  const deleteAllMetadata = useCallback(async () => {
    if (folderId === undefined) return;
    await deleteMetadataEntitiesFromProject(folderId);
    await invalidateAfterMutation();
    navigate(`/folder/${folderId}`);
  }, [folderId, invalidateAfterMutation, navigate]);

  return {
    entityTypes,
    currentMetadataClassId,
    pathFields,
    nonPathFields,
    invalidateAfterMutation,
    addInstance,
    deleteClass,
    deleteAllMetadata,
  };
}

export {useMetadataActions};
