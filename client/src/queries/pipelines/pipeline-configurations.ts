import {queryOptions} from '@tanstack/react-query';
import {loadPipelineConfigurations} from '../../api/pipeline/pipeline-api.ts';
import {QueryOptionsParams} from '../types.ts';

export const pipelineConfigurationKeys = {
  all: ['pipeline-configurations'] as const,
  details: () => [...pipelineConfigurationKeys.all, 'detail'] as const,
  detail: (id: number, version: string) =>
    [...pipelineConfigurationKeys.details(), id, version] as const,
};

export function pipelineConfigurationsQueryOptions(
  id: number | undefined,
  version: string | undefined,
  opts?: QueryOptionsParams,
) {
  const {enabled = true, ...queryOpts} = opts ?? {};
  return queryOptions({
    ...queryOpts,
    queryKey:
      id !== undefined && version !== undefined
        ? pipelineConfigurationKeys.detail(id, version)
        : pipelineConfigurationKeys.all,
    queryFn: () => loadPipelineConfigurations(id as number, version as string),
    enabled: enabled && id !== undefined && version !== undefined,
  });
}
