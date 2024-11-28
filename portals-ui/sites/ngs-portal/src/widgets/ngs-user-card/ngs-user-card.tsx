import { useMemo } from 'react';
import type { UserCardProps } from '@cloud-pipeline/components';
import { UserCard } from '@cloud-pipeline/components';
import { useSearchUserInfoByName } from '../../state/users-info/hooks';
import { useAuthenticationState } from '../../state/authentication/hooks';

export const NgsUserCard = (
  props: Omit<UserCardProps, 'user'> & { userName: string },
) => {
  const { userName, ...restProps } = props;
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
  return <UserCard user={user} {...restProps} />;
};
