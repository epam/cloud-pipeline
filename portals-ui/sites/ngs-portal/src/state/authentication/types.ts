import type { User, UserMetadata } from '@cloud-pipeline/core';

export type AuthenticationState = {
  authenticatedUser?: User | undefined;
  metadata?: UserMetadata;
  error: string | undefined;
  pending: boolean;
};

export type AuthenticationActions = {
  setAuthenticatedUser: (authenticatedUser: User | undefined) => void;
  setError: (error: string | undefined) => void;
  setPending: (pending: boolean) => void;
  setAuthenticationResult: (
    result: Pick<
      AuthenticationState,
      'authenticatedUser' | 'error' | 'metadata'
    >,
  ) => void;
  setMetadata: (metadata: UserMetadata) => void;
};

export type AuthenticationStore = AuthenticationState & AuthenticationActions;
