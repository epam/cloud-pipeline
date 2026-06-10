import {useEffect} from 'react';
import {useStore} from 'zustand';
import continuousFetch from '../../utils/continuous-fetch';
import {userNotificationsStore} from './user-notifications-store.ts';

const USER_NOTIFICATIONS_POLL_ID = 'navigation-user-notifications-count';
const USER_NOTIFICATIONS_POLL_INTERVAL_MS = 60_000;

export function useUserNotificationsCountPolling(): void {
  useEffect(() => {
    const {stop} = continuousFetch({
      identifier: USER_NOTIFICATIONS_POLL_ID,
      continuous: true,
      intervalMS: USER_NOTIFICATIONS_POLL_INTERVAL_MS,
      call: () => userNotificationsStore.getState().refresh(),
      fetchImmediate: true,
    });
    return () => {
      stop();
    };
  }, []);
}

export function useUnreadNotificationsCount(): number {
  return useStore(userNotificationsStore, (state) => state.totalCount);
}
