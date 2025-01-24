import { createStore } from 'zustand';
import type { AuthenticationState, AuthenticationStore } from './types.ts';
import type { User, UserMetadata } from '@cloud-pipeline/core';

const authenticationStore = createStore<AuthenticationStore>((set) => ({
  authenticatedUser: undefined,
  metadata: undefined,
  error: undefined,
  pending: false,
  setAuthenticationResult(
    result: Pick<
      AuthenticationState,
      'authenticatedUser' | 'error' | 'metadata'
    >,
  ) {
    const { authenticatedUser, error, metadata } = result;
    set({ authenticatedUser, error, metadata, pending: false });
  },
  setAuthenticatedUser(authenticatedUser: User | undefined) {
    set({ authenticatedUser });
  },
  setMetadata(metadata: UserMetadata | undefined) {
    set({ metadata });
  },
  setError(error: string | undefined) {
    set({ error });
  },
  setPending(pending: boolean) {
    set({ pending });
  },
}));

export { authenticationStore };
