import {createStore} from 'zustand';
import {UserInfo} from '../../@types/users.ts';

const authenticatedUserStore = createStore<{user: UserInfo}>((set) => ({
  user: {
    id: 0,
    userName: 'NOT_AUTHENTICATED',
    admin: false,
    blocked: false,
    mask: 0,
  },
}));

export {authenticatedUserStore};
