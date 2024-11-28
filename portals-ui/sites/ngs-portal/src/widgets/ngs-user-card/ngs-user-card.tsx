import { useMemo } from 'react';
import { UserCard, type CommonProps } from '@cloud-pipeline/components';
import { useSearchUserInfoByName } from '../../state/users-info/hooks';
import type { DropdownPlacement } from '@epam/uui-core';
import { useAuthenticationState } from '../../state/authentication/hooks';

type NgsUserCardProps = CommonProps & {
  userName: string;
  showTooltip?: boolean;
  tooltipPlacement?: DropdownPlacement;
  showIcon?: boolean;
  color?: 'neutral' | 'inverted' | 'critical';
  iconClassName?: string;
};

export const NgsUserCard = (props: NgsUserCardProps) => {
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
  if (!user) {
    return null;
  }
  return (
    <UserCard
      user={user}
      showTooltip={showTooltip}
      tooltipPlacement={tooltipPlacement}
      color={color}
      showIcon={showIcon}
      iconClassName={iconClassName}
      className={className}
      style={style}
    />
  );
};
