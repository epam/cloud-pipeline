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
};

export type UserInfo = Omit<User, 'userName' | 'admin' | 'blocked'> & {
  name: string;
};
