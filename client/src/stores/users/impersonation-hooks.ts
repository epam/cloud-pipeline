import {useCallback, useEffect} from 'react';
import {useStore} from 'zustand';
import {impersonationStore} from './impersonation-store.ts';

export function useImpersonationLoaded(): boolean {
  return useStore(impersonationStore, (state) => state.loaded);
}

export function useIsImpersonated(): boolean {
  return useStore(impersonationStore, (state) => Boolean(state.impersonatedUser));
}

export function useImpersonatedUserName(): string | undefined {
  return useStore(impersonationStore, (state) => state.impersonatedUser?.userName);
}

export function useImpersonationInitialization(): void {
  useEffect(() => {
    impersonationStore
      .getState()
      .load()
      .catch(() => undefined);
  }, []);
}

export function useStopImpersonation(): () => Promise<void> {
  const stopImpersonation = useStore(impersonationStore, (state) => state.stopImpersonation);
  return useCallback(() => stopImpersonation(), [stopImpersonation]);
}
