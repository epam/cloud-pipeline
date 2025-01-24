import type { AclClass } from '@cloud-pipeline/core';
import { usePermissions } from '../hooks';
import { useUsers } from '../../../state/users-info/hooks';
import { PermissionRow } from './permission-row';
import { PageSpinner } from '../../../shared/ui';

type Props = {
  entityId?: number;
  aclClass: AclClass;
};

export const Permissions = ({ entityId, aclClass }: Props) => {
  const { permissions, isLoading: isPermissionsLoading } = usePermissions(
    aclClass,
    entityId,
  );
  const users = useUsers();

  if (isPermissionsLoading) {
    return <PageSpinner />;
  }

  if (!entityId || !users.length) {
    return <div>No data</div>;
  }

  if (!permissions) {
    return <div>No permissions given</div>;
  }

  return (
    <div className="flex flex-wrap gap-x-4">
      {permissions?.map((permission) => (
        <PermissionRow
          key={permission.sid.name}
          permission={permission}
          usersInfo={users}
          className="flex-grow"
        />
      ))}
    </div>
  );
};
