import {useStore} from 'zustand';
import {UserInfo} from '../../@types/users.ts';
import {authenticatedUserStore} from './current-user.ts';

export function useAuthenticatedUser(): UserInfo {
  const {user} = useStore(authenticatedUserStore);
  return user;
}

export function useIsAdministrator(): boolean {
  return useAuthenticatedUser().admin ?? false;
}

const ROLE_BILLING_MANAGER = 'ROLE_BILLING_MANAGER';

export function useIsBillingManager(): boolean {
  const user = useAuthenticatedUser();
  return user.admin || (user.roles ?? []).some((role) => role.name === ROLE_BILLING_MANAGER);
}

export function getAuthenticatedUser(): UserInfo {
  return authenticatedUserStore.getState().user;
}
