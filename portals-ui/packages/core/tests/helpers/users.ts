import { AclClass, User, UserInfo } from '../../src';

export function generateUser(user: Partial<User>): User {
  return {
    aclClass: AclClass.user,
    admin: false,
    blocked: false,
    groups: [],
    roles: [],
    mask: 0,
    owner: 'unknown',
    id: 1,
    userName: 'user',
    ...user,
  };
}

export function generateUserInfo(user: User): UserInfo {
  const { userName, ...rest } = user;
  return {
    ...rest,
    name: userName,
  };
}
