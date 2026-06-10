import {useQuery} from '@tanstack/react-query';
import {useMemo} from 'react';
import {systemDictionariesQueryOptions} from './system-dictionaries.ts';
import {QueryOptionsParams} from '../types.ts';

export function useSystemDictionary(key: string | undefined, options?: QueryOptionsParams) {
  const {data: dictionaries = []} = useQuery(systemDictionariesQueryOptions(options));
  return useMemo(
    () => (key ? dictionaries.find((dictionary) => dictionary.key === key) : undefined),
    [dictionaries, key],
  );
}
