import { AclClass } from '@cloud-pipeline/core';
import { usePermissions } from '../../hooks';
import { useUsersInfoState } from '../../../../state/users-info/hooks';
import { PermissionRow } from './permission-row';
import { PageSpinner } from '../../../../shared/ui';

type Props = {
  projectId?: number;
};

export const ProjectPermissions = ({ projectId }: Props) => {
  const { permissions, isLoading: isPermissionsLoading } = usePermissions(
    AclClass.folder,
    projectId,
  );
  const { usersInfo } = useUsersInfoState();

  if (isPermissionsLoading) {
    return <PageSpinner />;
  }

  if (!projectId || !usersInfo) {
    return <div>No data</div>;
  }

  if (!permissions?.permissions) {
    return <div>No permissions given</div>;
  }

  return (
    <div className="flex flex-wrap gap-x-4">
      {permissions?.permissions?.map((permission) => {
        return (
          <PermissionRow
            permission={permission}
            usersInfo={usersInfo}
            className="flex-grow"
          />
        );
      })}
    </div>
  );
};
