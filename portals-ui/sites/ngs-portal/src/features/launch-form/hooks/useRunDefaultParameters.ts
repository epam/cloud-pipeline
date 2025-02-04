import { useMemo } from 'react';
import type { RunDefaultParameter } from '@cloud-pipeline/core';
import { fetchRunDefaultParameters } from '@cloud-pipeline/api';
import { useLoadableState } from '../../../shared/hooks';

export function useRunDefaultParameters(): {
  runDefaultParameters: RunDefaultParameter[] | undefined;
  error: string | undefined;
  pending: boolean;
} {
  const { state, pending, error } = useLoadableState(fetchRunDefaultParameters);
  return useMemo(
    () => ({
      runDefaultParameters: state,
      error,
      pending,
    }),
    [error, pending, state],
  );
}
