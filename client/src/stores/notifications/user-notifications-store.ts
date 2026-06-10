import {createStore} from 'zustand';
import {loadUserNotifications} from '../../api/notifications/user-notifications-api.ts';
import {getErrorDescription} from '../../utilities/errors.ts';

type UserNotificationsStore = {
  totalCount: number;
  pending: boolean;
  loaded: boolean;
  error?: string;
  refresh: () => Promise<number>;
};

const userNotificationsStore = createStore<UserNotificationsStore>((set, get) => ({
  totalCount: 0,
  pending: false,
  loaded: false,
  error: undefined,
  async refresh() {
    set({pending: true, error: undefined});
    try {
      const page = await loadUserNotifications();
      const totalCount = page.totalCount || 0;
      set({
        totalCount,
        loaded: true,
        pending: false,
        error: undefined,
      });
      return totalCount;
    } catch (error) {
      set({
        pending: false,
        error: getErrorDescription(error),
      });
      return get().totalCount;
    }
  },
}));

export {userNotificationsStore};
