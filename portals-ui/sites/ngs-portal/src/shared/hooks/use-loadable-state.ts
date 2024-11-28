import { useCallback, useMemo, useRef, useState } from 'react';
import { useAsyncEffect } from './use-async-effect.ts';

export type LoadableState<State> = {
  pending: boolean;
  error: string | undefined;
  state: State | undefined;
  reload: () => void;
};

export type LoadableStateFetchCallback<Args extends unknown[], State> = (
  ...args: Args
) => Promise<State>;

export function useLoadableStateWithInterval<Args extends unknown[], State>(
  intervalMs: number,
  f: LoadableStateFetchCallback<Args, State> | undefined,
  ...args: Args
): LoadableState<State> {
  const [state, setState] = useState<Omit<LoadableState<State>, 'reload'>>({
    pending: false,
    error: undefined,
    state: undefined,
  });
  const [reloadToken, setReloadToken] = useState({});
  const reload = useCallback(() => {
    setReloadToken({});
  }, [setReloadToken]);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(
    undefined,
  );
  const cancel = useCallback(() => {
    const { current } = timeoutRef;
    if (current) {
      clearTimeout(current);
    }
    timeoutRef.current = undefined;
  }, [timeoutRef]);
  const scheduleNext = useCallback(() => {
    cancel();
    if (intervalMs > 0) {
      timeoutRef.current = setTimeout(reload, intervalMs);
    }
  }, [timeoutRef, cancel, reload, intervalMs]);
  useAsyncEffect(
    async (cb) => {
      const commit = (st: Partial<LoadableState<State>>) => {
        cb(() => {
          setState((current) => ({
            ...current,
            ...st,
          }));
        });
      };
      if (f === undefined) {
        commit({ pending: false, error: undefined, state: undefined });
        return;
      }
      commit({ pending: true, error: undefined });
      try {
        const result = await f(...args);
        commit({ pending: false, error: undefined, state: result });
      } catch (error) {
        commit({
          pending: false,
          error: error instanceof Error ? error.message : String(error),
        });
      } finally {
        scheduleNext();
      }
    },
    cancel,
    [f, setState, reload, scheduleNext, reloadToken, ...args],
  );
  const { pending, error, state: result } = state;
  return useMemo(
    () => ({
      pending,
      error,
      state: result,
      reload,
    }),
    [pending, error, result, reload],
  );
}

export function useLoadableState<Args extends unknown[], State>(
  f: LoadableStateFetchCallback<Args, State> | undefined,
  ...args: Args
): LoadableState<State> {
  return useLoadableStateWithInterval(-1, f, ...args);
}
