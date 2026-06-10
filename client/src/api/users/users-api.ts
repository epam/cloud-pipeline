import type {Group, RoleInfo, RoleVO, UserInfo, UserVO} from '../../@types/users.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export {whoAmI} from './who-am-i.ts';

export async function loadUser(id: number): Promise<UserInfo> {
  return cloudPipelineApi.jsonGet<UserInfo>({uri: `user/${id}`});
}

export async function findUser(name: string): Promise<UserInfo> {
  return cloudPipelineApi.jsonGet<UserInfo>({uri: 'user/find', query: {name}});
}

export async function loadUsers(): Promise<UserInfo[]> {
  return cloudPipelineApi.jsonGet<UserInfo[]>({uri: 'users'});
}

export async function loadUsersInfo(): Promise<UserInfo[]> {
  type UserInfoRaw = {
    id: number;
    name: string;
    attributes?: UserInfo['attributes'];
    groups?: UserInfo['groups'];
    roles?: UserInfo['roles'];
  };
  const raw = await cloudPipelineApi.jsonGet<UserInfoRaw[]>({uri: 'users/info'});
  return raw.map((u) => ({
    id: u.id,
    userName: u.name,
    attributes: u.attributes,
    roles: u.roles,
    groups: u.groups,
    mask: 0,
    admin: false,
    blocked: false,
  }));
}

export async function createUser(user: UserVO): Promise<UserInfo> {
  return cloudPipelineApi.jsonPost<UserInfo>({uri: 'user', body: user});
}

export async function updateUser(id: number, user: UserVO): Promise<UserInfo> {
  return cloudPipelineApi.jsonPut<UserInfo>({uri: `user/${id}`, body: user});
}

export async function deleteUser(id: number): Promise<UserInfo> {
  return cloudPipelineApi.jsonDelete<UserInfo>({uri: `user/${id}`});
}

export async function blockUser(id: number): Promise<UserInfo> {
  return cloudPipelineApi.jsonPut<UserInfo>({uri: `user/${id}/block`});
}

export async function loadUserControls(): Promise<Record<string, unknown>> {
  return cloudPipelineApi.jsonGet({uri: 'user/controls'});
}

export async function isUserMember(group: string, user: string): Promise<boolean> {
  return cloudPipelineApi.jsonGet<boolean>({uri: 'user/isMember', query: {group, user}});
}

export async function loadGroups(): Promise<Group[]> {
  return cloudPipelineApi.jsonGet<Group[]>({uri: 'group'});
}

export async function findGroup(name: string): Promise<Group> {
  return cloudPipelineApi.jsonGet<Group>({uri: 'group/find', query: {name}});
}

export async function blockGroup(groupName: string): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: `group/${groupName}/block`});
}

export async function unblockGroup(groupName: string): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: `group/${groupName}/block`});
}

export async function loadAllRoles(): Promise<RoleInfo[]> {
  return cloudPipelineApi.jsonGet<RoleInfo[]>({uri: 'role/loadAll'});
}

export async function loadRole(id: number): Promise<RoleInfo> {
  return cloudPipelineApi.jsonGet<RoleInfo>({uri: `role/${id}`});
}

export async function createRole(role: RoleVO): Promise<RoleInfo> {
  return cloudPipelineApi.jsonPost<RoleInfo>({uri: 'role/create', body: role});
}

export async function updateRole(id: number, role: RoleVO): Promise<RoleInfo> {
  return cloudPipelineApi.jsonPut<RoleInfo>({uri: `role/${id}`, body: role});
}

export async function deleteRole(id: number): Promise<RoleInfo> {
  return cloudPipelineApi.jsonDelete<RoleInfo>({uri: `role/${id}`});
}

export async function assignRole(id: number, userName: string): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: `role/${id}/assign`, query: {userName}});
}

export async function removeRole(id: number, userName: string): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: `role/${id}/remove`, query: {userName}});
}
