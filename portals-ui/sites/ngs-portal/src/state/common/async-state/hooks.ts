import type { AsyncState } from './types.ts';
import { useEffect, useRef, useState } from 'react';

export function useAsyncState<
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  Load extends (...parameters: any) => Promise<any>,
  InitialData = Awaited<ReturnType<Load>> | undefined,
>(
  load: Load,
  defaultValue: InitialData,
  ...parameters: Parameters<Load>
): AsyncState<Awaited<ReturnType<Load>> | InitialData> {
  const [state, setState] = useState<
    AsyncState<Awaited<ReturnType<Load>> | InitialData>
  >({
    pending: false,
    error: undefined,
    data: defaultValue,
  });
  const token = useRef({});
  useEffect(() => {
    const t = {};
    token.current = t;
    const { current } = token;
    const commit = (fn: () => void) => {
      if (current === token.current) {
        fn();
      }
    };
    setState((c) => ({ ...c, pending: true, error: undefined }));
    load(...parameters)
      .then((data: Awaited<ReturnType<Load>>) => {
        commit(() => {
          setState({ pending: false, error: undefined, data });
        });
      })
      .catch((error) => {
        commit(() => {
          setState((c) => ({
            ...c,
            pending: false,
            error:
              error instanceof Error ? error.message : 'error fetching data',
          }));
        });
      });
    return () => {
      token.current = {};
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, load, setState, ...parameters]);
  return state;
}
