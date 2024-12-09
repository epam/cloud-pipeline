import { useCallback, useEffect, useMemo } from 'react';
import { Spinner } from '@epam/uui';
import { loadProjects } from '../../state/projects/load-projects';
import { useProjectsState } from '../../state/projects/hooks';
import { useSearch } from '../../shared/hooks/use-search.ts';
import { ProjectFilters } from './components/project-filters.tsx';
import { useProjectFilters, useProjectTags } from './hooks';
import { noop } from '@cloud-pipeline/core';
import { useUsersInfoState } from '../../state/users-info/hooks.ts';
import { loadUsersInfo } from '../../state/users-info/load-users-info.ts';
import { ProjectsList } from '../home/components/projects-list.tsx';

export function ProjectsPage() {
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);

  const { usersInfo, pending: isUserInfoPending, loaded } = useUsersInfoState();

  const handleOwnersFilterFocus = useCallback(() => {
    if (!usersInfo?.length && !isUserInfoPending && !loaded) {
      loadUsersInfo().then(noop).catch(noop);
    }
  }, [isUserInfoPending, loaded, usersInfo?.length]);

  const { isProjectMatchingFilters, handleFilterValueChange, tagsToFilter } =
    useProjectFilters();

  const { projects, error, pending } = useProjectsState();
  const {
    search,
    onSearchChange,
    filtered: searchedProjects,
  } = useSearch({
    items: projects ?? [],
  });

  const projectTags = useProjectTags({
    tagsToFilter,
    isProjectMatchingFilters,
    projects,
    users: usersInfo,
    searchedProjects,
  });

  const filteredProjects = useMemo(
    () =>
      searchedProjects.filter((project) => isProjectMatchingFilters(project)),
    [isProjectMatchingFilters, searchedProjects],
  );

  if (error) {
    return <div>{error}</div>;
  }

  if (pending) {
    return <Spinner />;
  }

  if (!projects) {
    return <div>No data</div>;
  }

  //todo: search refactoring needed (see <ItemsPanel /> search)
  return (
    <ProjectsList
      projects={filteredProjects}
      mode="extended"
      filters={
        <ProjectFilters
          projectTags={projectTags}
          onFilterValueChange={handleFilterValueChange}
          tagsToFilter={tagsToFilter}
          onOwnersFilterFocus={handleOwnersFilterFocus}
        />
      }
    />
  );
}
