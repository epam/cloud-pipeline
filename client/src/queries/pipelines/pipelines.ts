import {queryOptions} from '@tanstack/react-query';
import {loadAllPipelines} from '../../api/pipeline/pipeline-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const pipelinesKeys = {
  all: ['pipelines'] as const,
};

export function pipelinesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: pipelinesKeys.all,
    queryFn: loadAllPipelines,
    placeholderData: [],
  });
}

export function fetchPipelines(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(pipelinesQueryOptions(opts));
}
