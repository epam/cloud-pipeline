import {queryOptions} from '@tanstack/react-query';
import {loadAllRoles, loadUsers, loadUsersInfo} from '../../api/users/users-api.ts';
import {queryClient} from '../query-client.ts';
import {QueryOptionsParams} from '../types.ts';

export const usersKeys = {
  all: ['users'] as const,
};

export const usersInfoKeys = {
  all: ['users-info'] as const,
};

export const rolesKeys = {
  all: ['roles'] as const,
};

export function usersQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: usersKeys.all,
    queryFn: loadUsers,
    placeholderData: [],
  });
}

export function usersInfoQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: usersInfoKeys.all,
    queryFn: loadUsersInfo,
    placeholderData: [],
  });
}

export function rolesQueryOptions(opts?: QueryOptionsParams) {
  return queryOptions({
    ...(opts ?? {}),
    queryKey: rolesKeys.all,
    queryFn: loadAllRoles,
    placeholderData: [],
  });
}

export function fetchUsers(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(usersQueryOptions(opts));
}

export function fetchUsersInfo(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(usersInfoQueryOptions(opts));
}

export function fetchRoles(opts?: QueryOptionsParams) {
  return queryClient.fetchQuery(rolesQueryOptions(opts));
}
