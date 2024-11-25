import { useEffect, useState } from 'react';
import { initialize } from './initialize.ts';
import type { User } from '@cloud-pipeline/core';

export type InitializeState = {
  pending: boolean;
  error: string | undefined;
  completed: boolean;
  user?: User;
};

export function useInitializeApplication() {
  const [state, setState] = useState<InitializeState>({
    pending: true,
    error: undefined,
    completed: false,
  });

  useEffect(() => {
    void (async () => {
      try {
        setState((curr) => ({
          ...curr,
          pending: true,
          error: undefined,
        }));
        const user = await initialize();
        setState({
          pending: false,
          error: undefined,
          completed: true,
          user,
        });
      } catch (error) {
        setState({
          pending: false,
          error: error instanceof Error ? error.message : `${error}`,
          completed: false,
        });
      }
    })();
  }, [setState]);

  return state;
}
