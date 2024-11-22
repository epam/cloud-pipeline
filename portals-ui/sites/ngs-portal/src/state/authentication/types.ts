import type { User } from '@cloud-pipeline/core';

export type AuthenticationState = {
  authenticatedUser?: User | undefined;
  error: string | undefined;
  pending: boolean;
};

export type AuthenticationActions = {
  setAuthenticatedUser: (authenticatedUser: User | undefined) => void;
  setError: (error: string | undefined) => void;
  setPending: (pending: boolean) => void;
  setAuthenticationResult: (
    result: Pick<AuthenticationState, 'authenticatedUser' | 'error'>,
  ) => void;
};

export type AuthenticationStore = AuthenticationState & AuthenticationActions;
