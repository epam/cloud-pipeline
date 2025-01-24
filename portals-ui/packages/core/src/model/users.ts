import { AclEntry, AclClass } from './acl';

export type Role = AclEntry<AclClass.role> & {
  id: number;
  name: string;
  predefined: boolean;
  userDefault: boolean;
};

export type User = AclEntry<AclClass.user> & {
  id: number;
  userName: string;
  admin: boolean;
  attributes: Record<string, string | number>;
  blocked: boolean;
  groups: string[];
  roles: Role[];
  defaultStorageId?: number;
};

export type UserInfo = Omit<User, 'userName' | 'admin' | 'blocked'> & {
  name: string;
};

export type UserMetadata = Record<string, Record<string, unknown>>;
