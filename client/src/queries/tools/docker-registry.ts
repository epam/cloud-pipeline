import {queryOptions} from '@tanstack/react-query';
import {loadDockerRegistryTree} from '../../api/tools/tools-api.ts';
import {DockerRegistryList} from '../../@types/tools.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const dockerRegistryKeys = {
  all: ['docker-registry'] as const,
};

export function dockerRegistryQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: dockerRegistryKeys.all,
    queryFn: loadDockerRegistryTree,
    placeholderData: {registries: []} as DockerRegistryList,
  });
}

export function fetchDockerRegistryTree(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(dockerRegistryQueryOptions(opts));
}
