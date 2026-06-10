import {useMemo} from 'react';
import {preferenceNames} from '../preferences/names.ts';
import {
  useBooleanPreferenceValue,
  usePreference,
  usePreferenceLoaded,
} from '../../queries/preferences/hooks.ts';
import {
  useActiveRunsCount,
  useActiveRunsCountPolling,
  useActiveRunsCounterFilter,
} from '../runs/active-runs-count-hooks.ts';
import {
  useUnreadNotificationsCount,
  useUserNotificationsCountPolling,
} from '../notifications/user-notifications-hooks.ts';
import {
  useImpersonatedUserName,
  useImpersonationInitialization,
  useIsImpersonated,
  useStopImpersonation,
} from '../users/impersonation-hooks.ts';
import {useIsBillingManager} from '../users/hooks.ts';
import {NavigationItemContext} from './types.ts';

export function useNavigationPanelPolling(): void {
  useImpersonationInitialization();
  useActiveRunsCountPolling();
  useUserNotificationsCountPolling();
}

export function useNavigationItemContext(): NavigationItemContext {
  const isImpersonated = useIsImpersonated();
  const impersonatedUserName = useImpersonatedUserName();
  const stopImpersonation = useStopImpersonation();
  return useMemo(
    () => ({
      impersonation: {
        isImpersonated,
        impersonatedUserName,
        stopImpersonation,
      },
    }),
    [isImpersonated, impersonatedUserName, stopImpersonation],
  );
}

export function useBillingNavigationEnabled(): boolean {
  const isBillingManager = useIsBillingManager();
  const billingEnabled = useBooleanPreferenceValue(preferenceNames.billingReportsEnabled);
  const billingAdminsEnabled = useBooleanPreferenceValue(
    preferenceNames.billingReportsAdminsEnabled,
  );
  if (billingEnabled === undefined || billingAdminsEnabled === undefined) {
    return false;
  }
  return (!isBillingManager && billingEnabled) || (isBillingManager && billingAdminsEnabled);
}

export function useEmailNotificationsNavigationEnabled(): boolean {
  return useBooleanPreferenceValue(preferenceNames.systemNotificationsEnable) ?? false;
}

export {useActiveRunsCount, useActiveRunsCounterFilter, useUnreadNotificationsCount};
