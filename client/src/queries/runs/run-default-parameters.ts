import {queryOptions} from '@tanstack/react-query';
import {loadRunDefaultParameters} from '../../api/runs/runs-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const runDefaultParametersKeys = {
  all: ['run-default-parameters'] as const,
};

export function runDefaultParametersQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: runDefaultParametersKeys.all,
    queryFn: loadRunDefaultParameters,
    placeholderData: {} as unknown,
  });
}

export function fetchRunDefaultParameters(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(runDefaultParametersQueryOptions(opts));
}
