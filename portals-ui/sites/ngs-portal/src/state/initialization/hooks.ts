import { useEffect, useState } from 'react';
import { initialize } from './initialize.ts';

export type InitializeState = {
  pending: boolean;
  error: string | undefined;
  completed: boolean;
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
        await initialize();
        setState({
          pending: false,
          error: undefined,
          completed: true,
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
