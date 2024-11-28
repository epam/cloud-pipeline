import { fetchUsersInfo } from '@cloud-pipeline/api';
import type { UserInfo } from '@cloud-pipeline/core';
import { usersInfoStore } from './store.ts';

export async function loadUsersInfo(): Promise<UserInfo[]> {
  let usersInfo: UserInfo[] | undefined;
  let error: string | undefined;
  try {
    usersInfoStore.getState().setPending(true);
    usersInfo = await fetchUsersInfo();
    return usersInfo;
  } catch (authError) {
    error = authError instanceof Error ? authError.message : String(authError);
    throw new Error(error);
  } finally {
    usersInfoStore.getState().setUsersInfo({ usersInfo, error });
  }
}
