import {queryOptions} from '@tanstack/react-query';
import {loadPipeline} from '../../api/pipeline/pipeline-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const pipelineKeys = {
  all: ['pipeline'] as const,
  details: () => [...pipelineKeys.all, 'detail'] as const,
  detail: (id: number) => [...pipelineKeys.details(), id] as const,
};

export function pipelineQueryOptions(id: number | undefined, opts?: QueryOptionsParams) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  const queryKey = id !== undefined ? pipelineKeys.detail(id) : pipelineKeys.all;

  return queryOptions({
    ...queryOpts,
    queryKey,
    queryFn: () => loadPipeline(id as number),
    enabled: enabled && id !== undefined,
  });
}

export function fetchPipeline(id: number, opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(pipelineQueryOptions(id, opts));
}
