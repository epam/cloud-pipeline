import cloudPipelineApi from '../cloud-pipeline-api.ts';
import type {UserInfo} from '../../@types/users.ts';

export type PermissionSid = {
  name: string;
  principal: boolean;
};

export type Permission = {
  sid: PermissionSid;
  mask: number;
};

export type GrantEntity = {
  owner?: string;
  id?: number | string;
  aclClass?: string;
};

export type GrantGetResponse = {
  permissions?: Permission[];
  entity?: GrantEntity;
  owner?: string;
};

export type GrantPermissionVO = {
  aclClass: string;
  id: number | string;
  mask: number;
  principal: boolean;
  userName: string;
};

export async function loadGrant(id: number | string, type: string): Promise<GrantGetResponse> {
  return cloudPipelineApi.jsonGet<GrantGetResponse>({
    uri: 'grant',
    query: {id, aclClass: (type || '').toUpperCase()},
  });
}

export async function loadAllPermissions(
  entityId: number | string,
  entityClass: string,
): Promise<GrantGetResponse> {
  return cloudPipelineApi.jsonGet<GrantGetResponse>({
    uri: 'permissions',
    query: {id: entityId, aclClass: entityClass},
  });
}

export async function setGrantOwner(
  id: number | string,
  aclClass: string,
  userName: string,
): Promise<void> {
  await cloudPipelineApi.jsonPost({
    uri: 'grant/owner',
    query: {id, aclClass: aclClass.toUpperCase(), userName},
  });
}

export async function setGrantPermission(body: GrantPermissionVO): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: 'grant', body});
}

export async function removeGrantPermission(
  id: number | string,
  aclClass: string,
  user: string,
  isPrincipal: boolean,
): Promise<void> {
  await cloudPipelineApi.jsonDelete({
    uri: 'grant',
    query: {id, aclClass: aclClass.toUpperCase(), user, isPrincipal},
  });
}

export async function findUsersByPrefix(prefix: string): Promise<UserInfo[]> {
  return cloudPipelineApi.jsonGet<UserInfo[]>({uri: 'user/find', query: {prefix}});
}

export async function findGroupsByPrefix(prefix: string): Promise<string[]> {
  return cloudPipelineApi.jsonGet<string[]>({uri: 'group/find', query: {prefix}});
}
