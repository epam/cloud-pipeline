import { useCallback, useEffect, useMemo } from 'react';
import { Spinner } from '@epam/uui';
import { loadProjects } from '../../state/projects/load-projects';
import { useProjectsState } from '../../state/projects/hooks';
import { List, ListHeader } from '@cloud-pipeline/components';
import { useSearch } from '../../shared/hooks/use-search.ts';
import HighlightedText from '../../shared/highlight-text';
import { ProjectFilters } from './components/project-filters.tsx';
import { useProjectFilters, useProjectTags } from './hooks';
import { noop } from '@cloud-pipeline/core';
import { useUsersInfoState } from '../../state/users-info/hooks.ts';
import { loadUsersInfo } from '../../state/users-info/load-users-info.ts';

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

  return (
    <div className="flex flex-col overflow-auto">
      <ProjectFilters
        projectTags={projectTags}
        onFilterValueChange={handleFilterValueChange}
        tagsToFilter={tagsToFilter}
        onOwnersFilterFocus={handleOwnersFilterFocus}
      />
      <ListHeader
        title="Projects"
        className="shrink-0 border"
        search={search}
        onSearch={onSearchChange}
      />
      <List
        className="overflow-auto border-b border-l border-r"
        data={filteredProjects}
        renderItem={(project) => (
          <HighlightedText search={search}>{project.name}</HighlightedText>
        )}
        itemKey="id"
        sliced={20}
      />
    </div>
  );
}
