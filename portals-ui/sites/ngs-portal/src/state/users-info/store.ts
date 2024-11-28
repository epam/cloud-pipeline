import { createStore } from 'zustand';
import type { UsersInfoState, UsersInfoStore } from './types.ts';

const usersInfoStore = createStore<UsersInfoStore>((set) => ({
  usersInfo: undefined,
  error: undefined,
  pending: false,
  loaded: false,
  setUsersInfo(result: Pick<UsersInfoState, 'usersInfo' | 'error'>) {
    const { usersInfo, error } = result;
    set({ usersInfo, error, loaded: true, pending: false });
  },
  setError(error: string | undefined) {
    set({ error });
  },
  setPending(pending: boolean) {
    set({ pending });
  },
}));

export { usersInfoStore };
