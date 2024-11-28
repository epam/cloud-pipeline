import type { UsersInfoState, UsersInfoStore } from './types.ts';
import { useStore } from 'zustand';
import { usersInfoStore } from './store.ts';
import { useMemo } from 'react';
import { noop, type UserInfo } from '@cloud-pipeline/core';
import {
  compareUserNames,
  compareUserNamesWithoutDomain,
} from '../../shared/utils/users.ts';
import { loadUsersInfo } from './load-users-info.ts';

function useUsersInfoStore(): UsersInfoStore {
  return useStore(usersInfoStore);
}

export function useUsersInfoState(): UsersInfoState {
  const { usersInfo, pending, error, loaded } = useUsersInfoStore();
  return useMemo(
    () => ({
      usersInfo,
      pending,
      error,
      loaded,
    }),
    [usersInfo, pending, error, loaded],
  );
}

export function useSearchUserInfoByName(
  userName: string,
): UserInfo | undefined {
  const { usersInfo, pending, loaded } = useUsersInfoStore();
  if (!usersInfo && !pending && !loaded) {
    loadUsersInfo().then(noop).catch(noop);
  }
  const user = useMemo(() => {
    if (!usersInfo || userName === undefined || userName.length === 0) {
      return undefined;
    }
    let candidate = usersInfo.find((u) => compareUserNames(u.name, userName));
    if (!candidate) {
      candidate = usersInfo.find((u) =>
        compareUserNamesWithoutDomain(u.name, userName),
      );
    }
    return candidate;
  }, [usersInfo, userName]);
  return useMemo(() => user, [user]);
}
