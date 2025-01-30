import type { AclClass } from '@cloud-pipeline/core';
import { usePermissions } from '../hooks';
import { useUsers } from '../../../state/users-info/hooks';
import { PermissionRow } from './permission-row';
import { PageSpinner, PlaceholderText } from '../../../shared/ui';

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

  const renderContent = () => {
    if (isPermissionsLoading) {
      return <PageSpinner />;
    }

    if (!entityId || !users.length) {
      return <PlaceholderText>No data</PlaceholderText>;
    }

    if (!permissions) {
      return <PlaceholderText>No permissions given</PlaceholderText>;
    }

    return (
      <div className="flex flex-wrap gap-x-4">
        {permissions?.map((permission) => (
          <PermissionRow
            key={permission.sid.name}
            permission={permission}
            usersInfo={users}
            className="flex-1"
          />
        ))}
      </div>
    );
  };

  return (
    <div className="flex flex-col h-full">
      <b className="text-base mb-1">Permissions</b>
      {renderContent()}
    </div>
  );
};
