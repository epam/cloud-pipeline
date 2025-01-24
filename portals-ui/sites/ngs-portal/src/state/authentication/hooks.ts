import type { AuthenticationState, AuthenticationStore } from './types.ts';
import { useStore } from 'zustand';
import { authenticationStore } from './store.ts';
import { useMemo } from 'react';
import type { User, UserMetadata } from '@cloud-pipeline/core';

function useAuthenticationStore(): AuthenticationStore {
  return useStore(authenticationStore);
}

export function useAuthenticationState(): AuthenticationState {
  const { authenticatedUser, metadata, pending, error } =
    useAuthenticationStore();
  return useMemo(
    () => ({
      authenticatedUser,
      metadata,
      pending,
      error,
    }),
    [authenticatedUser, metadata, pending, error],
  );
}

export function useAuthenticatedUser(): User | undefined {
  return useAuthenticationState().authenticatedUser;
}

export function useAuthenticatedUserMetadata(): UserMetadata | undefined {
  return useAuthenticationState().metadata;
}
