import {queryOptions} from '@tanstack/react-query';
import {loadAllInstanceTypes, loadAllowedInstanceTypes} from '../../api/infra/infra-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const instancesKeys = {
  all: ['instances'] as const,
};

export const allowedInstancesKeys = {
  all: ['allowed-instances'] as const,
};

export function instancesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: instancesKeys.all,
    queryFn: loadAllInstanceTypes,
    placeholderData: [],
  });
}

export function allowedInstancesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: allowedInstancesKeys.all,
    queryFn: loadAllowedInstanceTypes,
    placeholderData: [],
  });
}

export function fetchInstances(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(instancesQueryOptions(opts));
}

export function fetchAllowedInstances(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(allowedInstancesQueryOptions(opts));
}
