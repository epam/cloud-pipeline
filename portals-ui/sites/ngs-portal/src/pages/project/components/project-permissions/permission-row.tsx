import type { PermissionsResponse } from '@cloud-pipeline/api';
import type { CommonProps } from '@cloud-pipeline/components';
import { UserCard } from '@cloud-pipeline/components';
import type { UserInfo } from '@cloud-pipeline/core';
import {
  readAllowedExtended,
  writeAllowedExtended,
  executeAllowedExtended,
  readInheritedExtended,
  writeInheritedExtended,
  executeInheritedExtended,
} from '@cloud-pipeline/core';
import { UserIcon, UsersIcon } from '@heroicons/react/24/solid';
import type { TagProps } from 'antd';
import { Tag } from 'antd';
import cn from 'classnames';

type PermissionTagProps = {
  isInherited: boolean;
  isAllowed: boolean;
  label: string;
  color: TagProps['color'];
};

const PermissionTag = ({
  isInherited,
  isAllowed,
  label,
  color,
}: PermissionTagProps) => {
  return (
    <Tag
      color={isAllowed ? color : 'default'}
      className={cn({
        'border-none text-gray-400': isInherited,
      })}>
      {label}
    </Tag>
  );
};

type PermissionRowProps = CommonProps & {
  permission: PermissionsResponse['permissions'][0];
  usersInfo: UserInfo[];
};

export const PermissionRow = ({
  permission,
  usersInfo,
  className,
  style,
}: PermissionRowProps) => {
  const { mask, sid } = permission;
  const isUser = sid.principal;

  const currentUser = isUser
    ? usersInfo?.find((user) => user.name === sid.name)
    : null;

  return (
    <div
      className={cn('px-3 py-2 border-b min-w-[220px]', className)}
      style={style}>
      <p className="flex items-center gap-x-0.5">
        {isUser ? (
          <UserIcon className="w-3 h-3 mr-0.5" />
        ) : (
          <UsersIcon className="w-3 h-3 mr-0.5" />
        )}

        {currentUser ? <UserCard user={currentUser} /> : <p>{sid.name}</p>}
      </p>

      <div className="flex gap-x-0.5 mt-2 ml-4">
        <PermissionTag
          isAllowed={readAllowedExtended(mask)}
          isInherited={readInheritedExtended(mask)}
          label="Read"
          color="blue"
        />
        <PermissionTag
          isAllowed={writeAllowedExtended(mask)}
          isInherited={writeInheritedExtended(mask)}
          label="Write"
          color="orange"
        />
        <PermissionTag
          isAllowed={executeAllowedExtended(mask)}
          isInherited={executeInheritedExtended(mask)}
          label="Execute"
          color="green"
        />
      </div>
    </div>
  );
};
