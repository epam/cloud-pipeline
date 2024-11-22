import { createStore } from 'zustand';
import type { AuthenticationState, AuthenticationStore } from './types.ts';
import type { User } from '@cloud-pipeline/core';

const authenticationStore = createStore<AuthenticationStore>((set) => ({
  authenticatedUser: undefined,
  error: undefined,
  pending: false,
  setAuthenticationResult(
    result: Pick<AuthenticationState, 'authenticatedUser' | 'error'>,
  ) {
    const { authenticatedUser, error } = result;
    set({ authenticatedUser, error, pending: false });
  },
  setAuthenticatedUser(authenticatedUser: User | undefined) {
    set({ authenticatedUser });
  },
  setError(error: string | undefined) {
    set({ error });
  },
  setPending(pending: boolean) {
    set({ pending });
  },
}));

export { authenticationStore };
