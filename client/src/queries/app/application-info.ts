import {queryOptions} from '@tanstack/react-query';
import {loadApplicationInfo} from '../../api/infra/infra-api.ts';
import {ApplicationInfo} from '../../@types/app.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const applicationInfoKeys = {
  all: ['application-info'] as const,
};

export function applicationInfoQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: applicationInfoKeys.all,
    queryFn: loadApplicationInfo,
    placeholderData: {} as ApplicationInfo,
  });
}

export function fetchApplicationInfo(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(applicationInfoQueryOptions(opts));
}
