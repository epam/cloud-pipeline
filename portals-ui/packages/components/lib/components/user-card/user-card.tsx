import { useCallback, useMemo } from 'react';
import classNames from 'classnames';
import type { CommonProps } from '@cloud-pipeline/components';
import { FlexCell, FlexRow, Tooltip } from '@epam/uui';
import {
  getUserDisplayName,
  type User,
  type UserInfo,
} from '@cloud-pipeline/core';
import type { DropdownPlacement } from '@epam/uui-core';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';

type UserCardProps = CommonProps & {
  user: User | UserInfo;
  showTooltip?: boolean;
  tooltipPlacement?: DropdownPlacement;
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
            <FlexCell width="auto">{userName}</FlexCell>
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
  if (!showTooltip || !user) {
    return (
      <FlexRow cx="whitespace-nowrap">
        {showIcon ? (
          <ContentPersonFillIcon
            className={classNames('fill-current h-5', iconClassName)}
          />
        ) : null}
        <span className={className} style={style}>
          {user ? getUserDisplayName(user) : userName}
        </span>
      </FlexRow>
    );
  }
  return (
    <span className="inline-flex whitespace-nowrap">
      {showIcon ? (
        <ContentPersonFillIcon
          className={classNames('fill-current h-5', iconClassName)}
        />
      ) : null}
      <Tooltip
        placement={tooltipPlacement}
        color={color}
        content={showTooltip ? renderContent(user) : null}>
        <span className={className} style={style}>
          {user ? getUserDisplayName(user) : userName}
        </span>
      </Tooltip>
    </span>
  );
};
