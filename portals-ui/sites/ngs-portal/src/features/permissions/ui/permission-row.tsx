import type { PermissionsResponse } from '@cloud-pipeline/api';
import type { CommonProps } from '@cloud-pipeline/components';
import { UserCard } from '@cloud-pipeline/components';
import type { UserInfo } from '@cloud-pipeline/core';
import {
  readAllowedExtended,
  writeAllowedExtended,
  executeAllowedExtended,
  readDeniedExtended,
  executeDeniedExtended,
  isAllPermissionsInheritedExtended,
} from '@cloud-pipeline/core';
import { UserIcon, UsersIcon } from '@heroicons/react/24/solid';
import type { TagProps } from 'antd';
import { Tag } from 'antd';
import { CheckIcon, XMarkIcon } from '@heroicons/react/24/outline';
import cn from 'classnames';

type PermissionTagProps = {
  isDenied: boolean;
  isAllowed: boolean;
  label: string;
  color: TagProps['color'];
};

const PermissionTag = ({
  isDenied,
  isAllowed,
  label,
  color,
}: PermissionTagProps) => {
  if (!isDenied && !isAllowed) {
    return null;
  }

  return (
    <Tag
      color={isDenied ? 'default' : color}
      className={cn('flex items-center', {
        'border-none text-gray-400': isDenied,
      })}>
      {isDenied ? (
        <XMarkIcon className="w-3 h-3" />
      ) : (
        <CheckIcon className="w-3 h-3" />
      )}
      <span className="ml-0.5">{label}</span>
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

  console.count('PermissionRow');

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

        {currentUser ? (
          <UserCard user={currentUser} />
        ) : (
          <span>{sid.name}</span>
        )}
      </p>

      {!isAllPermissionsInheritedExtended(mask) && (
        <div className="flex gap-x-0.5 mt-2 ml-4">
          <PermissionTag
            isAllowed={readAllowedExtended(mask)}
            isDenied={readDeniedExtended(mask)}
            label="Read"
            color="blue"
          />
          <PermissionTag
            isAllowed={writeAllowedExtended(mask)}
            isDenied={true}
            label="Write"
            color="orange"
          />
          <PermissionTag
            isAllowed={executeAllowedExtended(mask)}
            isDenied={executeDeniedExtended(mask)}
            label="Execute"
            color="green"
          />
        </div>
      )}
    </div>
  );
};
