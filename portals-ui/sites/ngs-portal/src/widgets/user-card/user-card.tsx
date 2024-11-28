import { useCallback, useMemo } from 'react';
import classNames from 'classnames';
import type { CommonProps } from '@cloud-pipeline/components';
import { FlexCell, FlexRow, Tooltip } from '@epam/uui';
import { useSearchUserInfoByName } from '../../state/users-info/hooks';
import type { User, UserInfo } from '@cloud-pipeline/core';
import { getUserDisplayName } from '../../shared/utils/users';
import type { DropdownPlacement } from '@epam/uui-core';
import ContentPersonFillIcon from '@epam/assets/icons/content-person-fill.svg?react';
import { useAuthenticationState } from '../../state/authentication/hooks';

type UserCardProps = CommonProps & {
  userName: string;
  showTooltip?: boolean;
  tooltipPlacement?: DropdownPlacement;
  showIcon?: boolean;
  color?: 'neutral' | 'inverted' | 'critical';
  iconClassName?: string;
};

export const UserCard = (props: UserCardProps) => {
  const {
    userName,
    showTooltip = true,
    tooltipPlacement,
    color = 'neutral',
    showIcon = false,
    iconClassName,
    className,
    style,
  } = props;
  const userInfo = useSearchUserInfoByName(userName);
  const { authenticatedUser } = useAuthenticationState();
  const user = useMemo(() => {
    if (authenticatedUser?.userName === userName) {
      return authenticatedUser;
    }
    return userInfo;
  }, [authenticatedUser, userInfo, userName]);
  const renderContent = useCallback(
    (user: User | UserInfo) => {
      if (user.attributes) {
        const attributes = Object.entries(user.attributes);
        return (
          <div className="flex flex-col gap-0.5">
            <FlexCell width="auto">{userName.toLowerCase()}</FlexCell>
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
      return userName.toLowerCase();
    },
    [userName],
  );
  if (!showTooltip || !user) {
    return (
      <FlexRow cx="whitespace-nowrap">
        {showIcon ? (
          <ContentPersonFillIcon
            className={classNames('fill-current h-5', iconClassName)}
          />
        ) : null}
        <span className={className} style={style}>
          {user ? getUserDisplayName(user) : userName.toLowerCase()}
        </span>
      </FlexRow>
    );
  }
  return (
    <FlexRow cx="whitespace-nowrap">
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
          {user ? getUserDisplayName(user) : userName.toLowerCase()}
        </span>
      </Tooltip>
    </FlexRow>
  );
};
