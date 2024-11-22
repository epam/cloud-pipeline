import { fetchAuthenticatedUser } from '@cloud-pipeline/api';
import type { User } from '@cloud-pipeline/core';
import { authenticationStore } from './store.ts';

/**
 * Returns authenticated user or throws an error
 */
export async function authenticate(): Promise<User> {
  let user: User | undefined;
  let error: string | undefined;
  try {
    authenticationStore.getState().setPending(true);
    user = await fetchAuthenticatedUser();
    return user;
  } catch (authError) {
    error = authError instanceof Error ? authError.message : `${authError}`;
    throw error;
  } finally {
    authenticationStore
      .getState()
      .setAuthenticationResult({ authenticatedUser: user, error });
  }
}
