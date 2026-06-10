import {queryOptions} from '@tanstack/react-query';
import {fetchPreference} from '../../api';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const PREFERENCES_STALE_TIME = 300_000;

export const preferenceKeys = {
  all: ['preferences'] as const,
  details: () => [...preferenceKeys.all, 'detail'] as const,
  detail: (name: string) => [...preferenceKeys.details(), name] as const,
};

export function preferenceQueryOptions(name: string, opts?: QueryOptionsParams) {
  const {enabled = true, ...queryOpts} = opts ?? {};

  return queryOptions({
    staleTime: PREFERENCES_STALE_TIME,
    ...queryOpts,
    queryKey: preferenceKeys.detail(name),
    queryFn: () => fetchPreference(name),
    enabled: enabled && !!name,
  });
}

export function fetchPreferenceValue(name: string, opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(preferenceQueryOptions(name, opts));
}
