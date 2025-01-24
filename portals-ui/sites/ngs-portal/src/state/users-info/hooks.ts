import type { UsersInfoStore } from './types.ts';
import { usersInfoStore } from './store.ts';
import { useMemo } from 'react';
import type { UserInfo } from '@cloud-pipeline/core';
import {
  compareUserNames,
  compareUserNamesWithoutDomain,
} from '@cloud-pipeline/core';
import { useLoadableStore } from '../common/loadable-store/hooks.ts';

function useUsersInfoStore(): UsersInfoStore {
  return useLoadableStore(usersInfoStore);
}

export function useUsersInfoState(): UsersInfoStore {
  return useUsersInfoStore();
}

export function useUsers(): UserInfo[] {
  return useUsersInfoState().data;
}

export function useSearchUserInfoByName(
  userName: string,
): UserInfo | undefined {
  const { data: usersInfo } = useUsersInfoStore();

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
