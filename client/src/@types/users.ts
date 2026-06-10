import {MaskedObject} from './common.ts';

export type RoleInfo = MaskedObject & {
  id: number;
  name: string;
  predefined: boolean;
  userDefault: boolean;
  owner?: string;
};

export type UserInfo = MaskedObject & {
  id: number;
  userName: string;
  admin: boolean;
  blocked: boolean;
  attributes?: Record<string, unknown>;
  email?: string;
  defaultStorageId?: number;
  firstLoginDate?: string;
  registrationDate?: string;
  groups?: string[];
  roles?: RoleInfo[];
  owner?: string;
};

export type UserVO = {
  id?: number;
  userName?: string;
  email?: string;
  admin?: boolean;
  blocked?: boolean;
  defaultStorageId?: number;
  groups?: string[];
  roleIds?: number[];
};

export type RoleVO = {
  id?: number;
  name?: string;
  userDefault?: boolean;
  predefined?: boolean;
};

export type Group = {
  name?: string;
  blocked?: boolean;
};
