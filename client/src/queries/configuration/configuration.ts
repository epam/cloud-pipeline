import {queryOptions} from '@tanstack/react-query';
import {loadConfiguration} from '../../api/configuration/configuration-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const configurationKeys = {
  all: ['configuration'] as const,
  details: () => [...configurationKeys.all, 'detail'] as const,
  detail: (id: number) => [...configurationKeys.details(), id] as const,
};

export function configurationQueryOptions(id: number | undefined, opts?: QueryOptionsParams) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  const queryKey = id !== undefined ? configurationKeys.detail(id) : configurationKeys.all;

  return queryOptions({
    ...queryOpts,
    queryKey,
    queryFn: () => loadConfiguration(id as number),
    enabled: enabled && id !== undefined,
  });
}

export function fetchConfiguration(id: number, opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(configurationQueryOptions(id, opts));
}
