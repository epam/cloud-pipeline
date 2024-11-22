import type { AuthenticationState, AuthenticationStore } from './types.ts';
import { useStore } from 'zustand';
import { authenticationStore } from './store.ts';
import { useMemo } from 'react';

function useAuthenticationStore(): AuthenticationStore {
  return useStore(authenticationStore);
}

export function useAuthenticationState(): AuthenticationState {
  const { authenticatedUser, pending, error } = useAuthenticationStore();
  return useMemo(
    () => ({
      authenticatedUser,
      pending,
      error,
    }),
    [authenticatedUser, pending, error],
  );
}
