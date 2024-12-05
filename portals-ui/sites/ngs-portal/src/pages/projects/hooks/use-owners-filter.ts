import { noop } from '@cloud-pipeline/core';
import { useCallback, useMemo } from 'react';
import { useUsersInfoState } from '../../../state/users-info/hooks';
import { loadUsersInfo } from '../../../state/users-info/load-users-info';

export const useOwnersFilter = () => {
  const { usersInfo, pending, loaded } = useUsersInfoState();

  const handleFocus = useCallback(() => {
    if (!usersInfo?.length && !pending && !loaded) {
      loadUsersInfo().then(noop).catch(noop);
    }
  }, [loaded, pending, usersInfo]);

  const usersWithStringId = useMemo(() => {
    return usersInfo?.map((user) => ({ ...user, id: user.name }));
  }, [usersInfo]);

  return useMemo(
    () => ({
      handleFocus,
      users: usersWithStringId,
    }),
    [handleFocus, usersWithStringId],
  );
};
