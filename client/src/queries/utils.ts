import {Query, QueryCache, QueryKey} from '@tanstack/react-query';
import {useQueryClient} from '@tanstack/react-query';
import {type RefObject, useCallback, useRef, useSyncExternalStore} from 'react';
import {getErrorDescription} from '../utilities/errors.ts';

export function getQueryErrorMessage(error: unknown): string | undefined {
  if (!error) {
    return undefined;
  }
  return getErrorDescription(error);
}

function queryKeyMatchesPrefix(queryKey: QueryKey, prefix: QueryKey): boolean {
  if (prefix.length > queryKey.length) {
    return false;
  }
  return prefix.every((part, index) => part === queryKey[index]);
}

function getQueriesSnapshot(cache: QueryCache, queryKeyPrefix: QueryKey): Query[] {
  return cache.findAll({queryKey: queryKeyPrefix});
}

function getQueriesSnapshotHash(queries: Query[]): string {
  return queries
    .map(
      (query) =>
        `${query.queryHash}:${query.state.dataUpdatedAt}:${query.state.status}:${query.state.fetchStatus}:${query.state.errorUpdatedAt}`,
    )
    .join('|');
}

function subscribeToQueriesByPrefix(
  cache: QueryCache,
  queryKeyPrefixRef: RefObject<QueryKey>,
  onStoreChange: () => void,
): () => void {
  let scheduled = false;
  const scheduleUpdate = () => {
    if (scheduled) {
      return;
    }
    scheduled = true;
    queueMicrotask(() => {
      scheduled = false;
      onStoreChange();
    });
  };

  return cache.subscribe((event) => {
    if ('query' in event && event.query) {
      if (!queryKeyMatchesPrefix(event.query.queryKey, queryKeyPrefixRef.current)) {
        return;
      }
    }
    scheduleUpdate();
  });
}

export function useQueriesByPrefix(queryKeyPrefix: QueryKey): Query[] {
  const queryClient = useQueryClient();
  const cache = queryClient.getQueryCache();
  const snapshotRef = useRef<{queries: Query[]; hash: string}>({queries: [], hash: ''});
  const queryKeyPrefixRef = useRef(queryKeyPrefix);
  queryKeyPrefixRef.current = queryKeyPrefix;

  const subscribe = useCallback(
    (onStoreChange: () => void) =>
      subscribeToQueriesByPrefix(cache, queryKeyPrefixRef, onStoreChange),
    [cache],
  );

  return useSyncExternalStore(
    subscribe,
    () => {
      const queries = getQueriesSnapshot(cache, queryKeyPrefix);
      const hash = getQueriesSnapshotHash(queries);
      if (snapshotRef.current.hash === hash) {
        return snapshotRef.current.queries;
      }
      snapshotRef.current = {queries, hash};
      return queries;
    },
    () => getQueriesSnapshot(cache, queryKeyPrefix),
  );
}
