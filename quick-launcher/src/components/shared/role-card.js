import React from 'react';
import {useUsersRoles} from '../utilities/users-roles-context';
import {getRoleDisplayName} from '../utilities/helpers';

export default function RoleCard(
  {
    roleName,
    className,
    small = false,
    style = {}
  }
) {
  const {
    roles
  } = useUsersRoles();
  const role = (roles || []).find(u => u.name === roleName);
  if (!role) {
    return (
      <div
        className={className}
        style={small ? {fontSize: 'smaller', ...style} : style}
      >
        {roleName}
      </div>
    );
  }
  return (
    <div
      className={className}
      style={small ? {fontSize: 'smaller', ...style} : style}
    >
      {getRoleDisplayName(role)}
    </div>
  );
}
