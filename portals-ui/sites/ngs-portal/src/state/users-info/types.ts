import type { UserInfo } from '@cloud-pipeline/core';

export type UsersInfoState = {
  usersInfo: UserInfo[] | undefined;
  error: string | undefined;
  pending: boolean;
  loaded: boolean;
};

export type UsersInfoActions = {
  setError: (error: string | undefined) => void;
  setPending: (pending: boolean) => void;
  setUsersInfo: (result: Pick<UsersInfoState, 'usersInfo' | 'error'>) => void;
};

export type UsersInfoStore = UsersInfoState & UsersInfoActions;
