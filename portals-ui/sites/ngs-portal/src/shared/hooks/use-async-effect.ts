import { useEffect, useMemo, useRef } from 'react';

export type CommitChangesFunction = () => void;

export type CommitActualChangesCallback = (f: CommitChangesFunction) => void;

/**
 * Used for applying state changes only if the passed callback was not aborted
 * (by calling callback when deps changed). Usage:
 * ```typescript
 * useAsyncEffect(async (commit) => {
 *   commit(() => { setState({ pending: true, result: undefined }); });
 *   const res = await someAsyncFunction(param1, param2);
 *   // setState of the next line will only be called if another
 *   // call of useAsyncEffect was not triggered (i.e., deps did not change).
 *   commit(() => { setState({ pending: false, result: res }); });
 * }, [setState, someAsyncFunction, param1, param2]);
 *
 * ```
 * @param callback
 * @param deps
 */
function useAsyncEffect(
  callback: (cb: CommitActualChangesCallback) => Promise<void>,
  deps: unknown[],
): void;
function useAsyncEffect(
  callback: (cb: CommitActualChangesCallback) => Promise<void>,
  onCancel: () => void,
  deps: unknown[],
): void;
function useAsyncEffect(
  callback: (cb: CommitActualChangesCallback) => Promise<void>,
  arg2: unknown,
  arg3?: unknown,
): void {
  const { onCancel, deps } = useMemo<{
    onCancel: (() => void) | undefined;
    deps: unknown[];
  }>(() => {
    if (typeof arg2 === 'function') {
      return {
        onCancel: arg2 as () => void,
        deps: arg3 as unknown[],
      };
    }
    return {
      onCancel: undefined,
      deps: arg2 as unknown[],
    };
  }, [arg2, arg3]);
  const fetchTokenRef = useRef({});
  useEffect(() => {
    const newToken = {};
    fetchTokenRef.current = newToken;
    const cb: CommitActualChangesCallback = (f: CommitChangesFunction) => {
      if (newToken === fetchTokenRef.current) {
        f();
      }
    };
    void callback(cb);
    return () => {
      fetchTokenRef.current = {};
      if (onCancel) {
        onCancel();
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    // We're not including callback here intentionally
    fetchTokenRef,
    onCancel,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    ...deps,
  ]);
}

export { useAsyncEffect };
