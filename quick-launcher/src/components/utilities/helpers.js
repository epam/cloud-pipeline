export function filterUserFn (filter) {
  return function filterFn(user) {
    return !filter ||
      (user.name || '').toLowerCase().includes(filter.toLowerCase()) ||
      !!Object.values(user.attributes || {})
        .find(attributeValue => (attributeValue || '').toLowerCase()
          .includes(filter.toLowerCase())
        )
  };
}

export function filterRoleFn (filter) {
  return function filterFn(role) {
    return !filter || (role.name || '').toLowerCase().includes(filter.toLowerCase())
  };
}

export function getRoleDisplayName (role) {
  if (!role || !role.name) {
    return undefined;
  }
  if (!role.predefined && role.name.toLowerCase().indexOf('role_') === 0) {
    return role.name.substring('role_'.length);
  }
  return role.name;
}
