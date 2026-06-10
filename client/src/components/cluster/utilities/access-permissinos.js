const ROLE_CLUSTER_READER = 'ROLE_CLUSTER_READER';

export function isAdmin(user) {
  if (user) {
    return user.admin;
  }
  return false;
}

export function isClusterReader(user) {
  if (isAdmin(user)) {
    return true;
  }
  if (user) {
    const {roles = []} = user;
    return roles.some((role) => role.name.toLowerCase() === ROLE_CLUSTER_READER.toLowerCase());
  }
  return false;
}
