import {queryOptions} from '@tanstack/react-query';
import {loadCloudRegions} from '../../api/cloud-regions/load-regions.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const cloudRegionKeys = {
  all: ['cloud-regions'] as const,
};

export function cloudRegionsQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: cloudRegionKeys.all,
    queryFn: loadCloudRegions,
  });
}

export function fetchCloudRegions(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(cloudRegionsQueryOptions(opts));
}
