import {queryOptions} from '@tanstack/react-query';
import {loadSystemDictionaries} from '../../api/system-dictionaries/system-dictionaries-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const systemDictionaryKeys = {
  all: ['system-dictionaries'] as const,
};

export function systemDictionariesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: systemDictionaryKeys.all,
    queryFn: loadSystemDictionaries,
    placeholderData: [],
  });
}

export function fetchSystemDictionaries(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(systemDictionariesQueryOptions(opts));
}
