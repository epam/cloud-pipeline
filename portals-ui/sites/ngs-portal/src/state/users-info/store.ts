import type { UsersInfoStore } from './types.ts';
import createLoadableStore from '../common/loadable-store/create-loadable-store.ts';
import { fetchUsersInfo } from '@cloud-pipeline/api';

const usersInfoStore = createLoadableStore<UsersInfoStore>(
  fetchUsersInfo,
  [],
  () => ({}),
);

export { usersInfoStore };
