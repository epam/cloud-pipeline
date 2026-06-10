import {useCallback, useMemo} from 'react';
import {message} from 'antd';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {
  deleteDataStorageTags,
  deleteMetadataKeys,
  saveDataStorageTags,
  updateMetadataKeys,
} from '../../api';
import {
  dataStorageTagsKeys,
  dataStorageTagsQueryOptions,
  getQueryErrorMessage,
  metadataEntityKeys,
  metadataEntityQueryOptions,
  systemDictionariesQueryOptions,
} from '../../queries';
import {MetadataEntityData} from '../../@types/metadata.ts';
import {
  buildMetadataItems,
  getCascadeValues,
  isSpecialItem,
  toMetadataEntityData,
  toMetadataEntityRef,
} from './utilities.ts';
import {ApplyChanges, MetadataChangeItem, MetadataContext, MetadataProps} from './types.ts';

type ApplyResult = {error?: string; refresh: boolean};

export function useMetadataController(props: MetadataProps, context?: MetadataContext) {
  const {
    value,
    extraKeys = [],
    restrictedKeys = [],
    applyChanges = ApplyChanges.inline,
    onChange,
  } = props;

  const queryClient = useQueryClient();
  const storageId = context?.isDataStorageTags ? Number(context.entityParentId) : undefined;
  const storagePath = context?.isDataStorageTags ? String(context.entityId) : undefined;
  const storageVersion = context?.entityVersion;

  const metadataEntity = context?.metadataEntity;
  const loadMetadataEntity = !!metadataEntity;

  const {
    data: entityData,
    isFetching: entityPending,
    isSuccess: entityLoaded,
    error: entityQueryError,
  } = useQuery({
    ...metadataEntityQueryOptions(metadataEntity, {enabled: loadMetadataEntity}),
  });
  const entityError = getQueryErrorMessage(entityQueryError);

  const {
    data: tags,
    isFetching: tagsPending,
    isSuccess: tagsLoaded,
    error: tagsQueryError,
  } = useQuery({
    ...dataStorageTagsQueryOptions(storageId, storagePath, storageVersion, {
      enabled: !!context?.isDataStorageTags,
    }),
  });
  const tagsError = getQueryErrorMessage(tagsQueryError);

  const {data: dictionaries = []} = useQuery(systemDictionariesQueryOptions());

  const pending = props.pending || (context?.isDataStorageTags ? tagsPending : entityPending);
  const loaded = context?.isDataStorageTags ? tagsLoaded : entityLoaded;
  const error = context?.isDataStorageTags ? tagsError : entityError;

  const items = useMemo(
    () =>
      buildMetadataItems({
        value,
        entityData,
        tags: context?.isDataStorageTags ? tags : undefined,
        extraKeys,
      }),
    [value, entityData, tags, context?.isDataStorageTags, extraKeys],
  );

  const isReadOnlyTag = useCallback(
    (tag: string) => (restrictedKeys ?? []).includes(tag),
    [restrictedKeys],
  );

  const reload = useCallback(async () => {
    if (!context) {
      return;
    }
    if (context.isDataStorageTags && storageId && storagePath) {
      await queryClient.invalidateQueries({
        queryKey: dataStorageTagsKeys.detail(storageId, storagePath, storageVersion),
      });
      return;
    }
    const entityRef = toMetadataEntityRef(context);
    if (entityRef) {
      await queryClient.invalidateQueries({
        queryKey: metadataEntityKeys.detail(entityRef.entityId, entityRef.entityClass),
      });
    }
  }, [context, queryClient, storageId, storagePath, storageVersion]);

  const applyChangesInternal = useCallback(
    async (
      modified: MetadataChangeItem[] = [],
      removed: MetadataChangeItem[] = [],
    ): Promise<ApplyResult> => {
      if (!context) {
        return {refresh: false};
      }
      if (applyChanges === ApplyChanges.callback && onChange) {
        const baseItems = items
          .map(({key, value: itemValue, type}) => ({key, value: itemValue, type}))
          .filter(({key}) => !isReadOnlyTag(key) && !removed.find((item) => item.key === key));
        const metadata = [...baseItems, ...modified].reduce<MetadataEntityData>((acc, item) => {
          acc[item.key] = {value: item.value, type: item.type || 'string'};
          return acc;
        }, {});
        await onChange(metadata);
        return {refresh: false};
      }

      try {
        if (context.isDataStorageTags) {
          if (!storageId || !storagePath) {
            return {refresh: false};
          }
          if (removed.length > 0) {
            await deleteDataStorageTags(
              storageId,
              storagePath,
              removed.map((item) => item.key),
              storageVersion,
            );
          }
          if (modified.length > 0) {
            const payload = modified.reduce<Record<string, string>>((acc, item) => {
              if (item.value !== undefined) {
                acc[item.key] = item.value;
              }
              return acc;
            }, {});
            await saveDataStorageTags(storageId, storagePath, payload, storageVersion, false);
          }
        } else {
          const entityRef = toMetadataEntityRef(context);
          if (!entityRef) {
            return {refresh: false};
          }
          if (removed.length > 0) {
            await deleteMetadataKeys({
              entity: entityRef,
              data: toMetadataEntityData(removed),
            });
          }
          if (modified.length > 0) {
            await updateMetadataKeys({
              entity: entityRef,
              data: toMetadataEntityData(modified.filter((item) => item.value !== undefined)),
            });
          }
        }
        return {refresh: true};
      } catch (e) {
        return {error: e instanceof Error ? e.message : String(e), refresh: false};
      }
    },
    [applyChanges, context, isReadOnlyTag, items, onChange, storageId, storagePath, storageVersion],
  );

  const applyRemoveChanges = useCallback(
    async ({
      item,
      all = false,
    }: {
      item?: MetadataChangeItem;
      all?: boolean;
    }): Promise<ApplyResult> => {
      if (all) {
        return applyChangesInternal(
          [],
          items.filter(({key}) => !isReadOnlyTag(key) && !isSpecialItem(key)),
        );
      }
      if (item) {
        return applyChangesInternal([], [item]);
      }
      return {refresh: false};
    },
    [applyChangesInternal, isReadOnlyTag, items],
  );

  const applyValues = useCallback(
    async (values: MetadataChangeItem[]) => {
      const modified = values.map(({key, value: itemValue}) => {
        const current = items.find((item) => item.key === key);
        return current ? {...current, value: itemValue} : {key, value: itemValue};
      });
      const {error: applyError, refresh} = await applyChangesInternal(modified);
      if (applyError) {
        message.error(applyError, 5);
        return false;
      }
      if (refresh) {
        await reload();
      }
      return true;
    },
    [applyChangesInternal, items, reload],
  );

  const applyCascadeValues = useCallback(
    async (key: string, itemValue: string, secret = false) => {
      const {result: values, warnings} = getCascadeValues(key, itemValue, secret, dictionaries);
      if (warnings.size > 0) {
        message.warning(
          `Error auto-filling attributes: circular dependency for "${[...warnings].join(', ')}" dictionar${warnings.size > 1 ? 'ies' : 'y'}`,
          5,
        );
      }
      return applyValues(values);
    },
    [applyValues, dictionaries],
  );

  return {
    items,
    pending,
    loaded,
    error,
    dictionaries,
    isReadOnlyTag,
    reload,
    applyChangesInternal,
    applyRemoveChanges,
    applyValues,
    applyCascadeValues,
    context,
  };
}
