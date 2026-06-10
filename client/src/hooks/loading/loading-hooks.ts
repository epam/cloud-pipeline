import {LoadableData, LoadingHookState} from './types.ts';
import {useEffect, useRef, useState} from 'react';
import {getErrorDescription} from '../../utilities/errors.ts';

export function useLoadingHook<A extends unknown[], T>(
  loadableData: LoadableData<A, T>,
  ...args: A
): LoadingHookState<T> {
  const [state, setState] = useState<LoadingHookState<T>>({
    pending: true,
    loaded: false,
  });
  const token = useRef({});
  useEffect(
    () => {
      const t = (token.current = {});
      (async () => {
        const commit = (o: Partial<LoadingHookState<T>>) => {
          if (t === token.current) {
            setState((current) => ({
              ...current,
              ...o,
            }));
          }
        };
        try {
          commit({pending: true});
          const data = await loadableData(...args);
          commit({data, loaded: true});
        } catch (error) {
          commit({error: getErrorDescription(error), loaded: false});
        } finally {
          commit({pending: false});
        }
      })();
      return () => {
        token.current = {}; // invalidate token
      };
    },
    ([setState] as unknown[]).concat([loadableData]).concat(args),
  );
  return state;
}
