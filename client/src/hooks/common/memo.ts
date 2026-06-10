import {useMemo} from 'react';

export function useMemoizedObject<T extends object>(o: T): T {
  const stringified = JSON.stringify(o);
  return useMemo(() => JSON.parse(stringified) as T, [stringified]);
}

export function useMemoizedArray<T extends unknown[]>(o: T): T {
  return useMemoizedObject(o);
}
