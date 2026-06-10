import {useMemo} from 'react';
import {MetadataLoadResponseItem} from '../../@types/metadata.ts';
import {useMemoizedArray} from '../../hooks/common/memo.ts';
import {MetadataEntityInfo} from '../../stores/metadata/types.ts';
import {mergeMetadataEntityInfos} from '../../stores/metadata/utilities.ts';
import {useQueriesByPrefix} from '../utils.ts';
import {metadataFolderKeys} from './metadata.ts';

export function useMetadataFolderEntities(options?: {
  entityClasses?: string[];
  onlyLoaded?: boolean;
}): MetadataEntityInfo[] {
  const {entityClasses = [], onlyLoaded = false} = options ?? {};
  const classes = useMemoizedArray(entityClasses);
  const queries = useQueriesByPrefix(metadataFolderKeys.all);

  return useMemo<MetadataEntityInfo[]>(() => {
    const entities = queries
      .filter((query) => !onlyLoaded || query.state.status === 'success')
      .flatMap((query) => {
        const folderId = query.queryKey[2] as number | undefined;
        if (folderId === undefined) {
          return [];
        }
        const items = (query.state.data ?? []) as MetadataLoadResponseItem[];
        return items.map((v) => ({
          loaded: query.state.status === 'success',
          pending: query.state.fetchStatus === 'fetching',
          error: query.state.error ? String(query.state.error) : undefined,
          data: v.data,
          entity: v.entity ?? {entityId: folderId, entityClass: 'FOLDER'},
          issuesCount: v.issuesCount,
          timestamp: query.state.dataUpdatedAt,
        }));
      })
      .filter((v) => classes.length === 0 || classes.includes(v.entity?.entityClass ?? ''));

    return mergeMetadataEntityInfos(entities);
  }, [queries, classes, onlyLoaded]);
}
