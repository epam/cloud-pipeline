import {useQuery} from '@tanstack/react-query';
import {useMemo} from 'react';
import {Tool} from '../../@types/tools.ts';
import {dockerRegistryQueryOptions} from './docker-registry.ts';
import {QueryOptionsParams} from '../types.ts';

export function useTools(options?: QueryOptionsParams): Tool[] {
  const {data: {registries} = {registries: []}} = useQuery(dockerRegistryQueryOptions(options));
  return useMemo(() => {
    let result: Tool[] = [];
    for (const registry of registries ?? []) {
      for (const group of registry.groups ?? []) {
        result = result.concat(
          (group.tools ?? []).map((tool) => ({
            ...tool,
            toolGroupRef: group,
            toolGroupId: group.id,
            registryRef: registry,
            registryId: registry.id,
          })),
        );
      }
    }
    return result;
  }, [registries]);
}
