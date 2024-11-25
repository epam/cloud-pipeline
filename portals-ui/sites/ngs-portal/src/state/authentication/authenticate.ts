import { fetchAuthenticatedUser } from '@cloud-pipeline/api';
import type { User } from '@cloud-pipeline/core';
import { authenticationStore } from './store.ts';

/**
 * Returns authenticated user or throws an error
 */
export async function authenticate(): Promise<User> {
  const store = authenticationStore.getState();
  store.setPending(true);

  try {
    const user = await fetchAuthenticatedUser();

    store.setAuthenticationResult({
      authenticatedUser: user,
      error: undefined,
    });

    return user;
  } catch (authError) {
    const errorMessage =
      authError instanceof Error ? authError.message : String(authError);

    store.setAuthenticationResult({
      authenticatedUser: undefined,
      error: errorMessage,
    });

    throw new Error(errorMessage);
  }
}
