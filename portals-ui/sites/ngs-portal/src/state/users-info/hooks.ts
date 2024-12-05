import type { UsersInfoState, UsersInfoStore } from './types.ts';
import { useStore } from 'zustand';
import { usersInfoStore } from './store.ts';
import { useEffect, useMemo } from 'react';
import type { UserInfo } from '@cloud-pipeline/core';
import {
  compareUserNames,
  compareUserNamesWithoutDomain,
  noop,
} from '@cloud-pipeline/core';
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

  useEffect(() => {
    if (!usersInfo && !pending && !loaded) {
      loadUsersInfo().then(noop).catch(noop);
    }
  }, [loaded, pending, usersInfo]);

  return useMemo(() => {
    if (!usersInfo || !userName) {
      return undefined;
    }

    return (
      usersInfo.find((u) => compareUserNames(u.name, userName)) ??
      usersInfo.find((u) => compareUserNamesWithoutDomain(u.name, userName))
    );
  }, [usersInfo, userName]);
}
