import type { AuthenticationStore } from './types.ts';
import { authenticationStore } from './store.ts';
import type { User, UserMetadata } from '@cloud-pipeline/core';
import { useLoadableStore } from '../common/loadable-store/hooks.ts';

function useAuthenticationStore(): AuthenticationStore {
  return useLoadableStore(authenticationStore);
}

export function useAuthenticatedUser(): User | undefined {
  return useAuthenticationStore().data?.user;
}

export function useAuthenticatedUserMetadata(): UserMetadata | undefined {
  return useAuthenticationStore().data?.metadata;
}
