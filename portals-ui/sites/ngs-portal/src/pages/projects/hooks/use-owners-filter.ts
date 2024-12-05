import type { Project } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import { useCallback, useMemo } from 'react';
import { useUsersInfoState } from '../../../state/users-info/hooks';
import { loadUsersInfo } from '../../../state/users-info/load-users-info';

export const useOwnersFilter = (filteredProjects: Project[]) => {
  const { usersInfo, pending, loaded } = useUsersInfoState();

  const handleFocus = useCallback(() => {
    if (!usersInfo?.length && !pending && !loaded) {
      loadUsersInfo().then(noop).catch(noop);
    }
  }, [loaded, pending, usersInfo]);

  const preparedUsers = useMemo(() => {
    return usersInfo?.map((user) => {
      const projectsCount = filteredProjects.filter(
        (project) => project.owner === user.name,
      ).length;

      return {
        name: `${user.name} (${projectsCount})`,
        id: user.name,
        count: projectsCount,
      };
    });
  }, [filteredProjects, usersInfo]);

  return useMemo(
    () => ({
      handleFocus,
      users: preparedUsers,
    }),
    [handleFocus, preparedUsers],
  );
};
