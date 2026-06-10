import {queryOptions} from '@tanstack/react-query';
import {loadPipelineVersions} from '../../api/pipeline/pipeline-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const pipelineVersionKeys = {
  all: ['pipeline-version'] as const,
  details: () => [...pipelineVersionKeys.all, 'detail'] as const,
  detail: (id: number) => [...pipelineVersionKeys.details(), id] as const,
};

export function pipelineVersionsQueryOptions(id: number | undefined, opts?: QueryOptionsParams) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  const queryKey = id !== undefined ? pipelineVersionKeys.detail(id) : pipelineVersionKeys.all;

  return queryOptions({
    ...queryOpts,
    queryKey,
    queryFn: () => loadPipelineVersions(id as number),
    enabled: enabled && id !== undefined,
  });
}

export function fetchPipelineVersions(id: number, opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(pipelineVersionsQueryOptions(id, opts));
}
