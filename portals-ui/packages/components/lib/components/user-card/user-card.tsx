import { useCallback, useMemo } from 'react';
import classNames from 'classnames';
import type { CommonProps } from '@cloud-pipeline/components';
import { Popover } from 'antd';
import { getUserDisplayName } from '@cloud-pipeline/core';
import type { User, UserInfo } from '@cloud-pipeline/core';
import type { TooltipPlacement } from 'antd/es/tooltip';
import { UserIcon } from '@heroicons/react/24/solid';

export type UserCardProps = CommonProps & {
  user: User | UserInfo;
  showTooltip?: boolean;
  tooltipPlacement?: TooltipPlacement;
  showIcon?: boolean;
  color?: 'neutral' | 'inverted' | 'critical';
  iconClassName?: string;
};

export const UserCard = (props: UserCardProps) => {
  const {
    user,
    showTooltip = true,
    tooltipPlacement,
    color = 'neutral',
    showIcon = false,
    iconClassName,
    className,
    style,
  } = props;

  const userName = useMemo(() => {
    if ('name' in user && typeof user.name === 'string') {
      return user.name;
    }
    if ('userName' in user && typeof user.userName === 'string') {
      return user.userName.toLowerCase();
    }
    return getUserDisplayName(user);
  }, [user]);

  const renderContent = useCallback(
    (user: User | UserInfo) => {
      if (user.attributes) {
        const attributes = Object.entries(user.attributes);
        return (
          <div className="flex flex-col gap-0.5">
            <div className="w-auto">{userName}</div>
            <table className="table-auto">
              <tbody>
                {attributes.map(([key, value]) => (
                  <tr key={key} className="text-xs">
                    <td className="p-0 pr-2">{key}</td>
                    <td className="p-0">{value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      }
      return userName;
    },
    [userName],
  );

  if (!user) {
    return null;
  }

  const icon = showIcon ? (
    <UserIcon className={classNames('w-3 h-3 mr-0.5', iconClassName)} />
  ) : null;

  const userComponent = (
    <span className={className} style={style}>
      {user ? getUserDisplayName(user) : userName}
    </span>
  );

  if (!showTooltip || !user) {
    return (
      <div className="inline-flex whitespace-nowrap items-center">
        {icon}
        {userComponent}
      </div>
    );
  }

  return (
    <span className="inline-flex whitespace-nowrap items-center">
      {icon}
      <Popover
        placement={tooltipPlacement}
        color={color}
        title={showTooltip ? renderContent(user) : null}>
        {userComponent}
      </Popover>
    </span>
  );
};
