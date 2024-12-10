import { useEffect, useMemo } from 'react';
import { Spinner } from '@epam/uui';
import { loadProjects } from '../../state/projects/load-projects';
import { useProjectsState } from '../../state/projects/hooks';
import { useSearch } from '../../shared/hooks/use-search.ts';
import { useNgsFilters, useNgsTags } from './hooks';
import { ProjectsList } from '../home/components/projects-list.tsx';
import { projectFiltersToDisplay } from './constants.ts';

export function ProjectsPage() {
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);

  const { projects, error, pending } = useProjectsState();

  const {
    search,
    onSearchChange,
    filtered: searchedProjects,
  } = useSearch({
    items: projects ?? [],
  });

  const {
    tagsToFilter,
    usersInfo,
    isMatchingFilters,
    handleFilterValueChange,
    handleOwnersFilterFocus,
  } = useNgsFilters();

  const projectTags = useNgsTags({
    tagsToFilter,
    isMatchingFilters,
    items: projects,
    users: usersInfo,
    searchedItems: searchedProjects,
    filtersToDisplay: projectFiltersToDisplay,
  });

  const filteredProjects = useMemo(
    () => searchedProjects.filter((project) => isMatchingFilters(project)),
    [isMatchingFilters, searchedProjects],
  );

  if (error) {
    return <div>{error}</div>;
  }

  if (pending && (!projects || projects.length === 0)) {
    return <Spinner />;
  }

  if (!projects) {
    return <div>No data</div>;
  }

  //todo: search refactoring needed (see <ItemsPanel /> search)
  return (
    <div className="p-3 overflow-hidden h-full w-full">
      <ProjectsList
        projects={filteredProjects}
        mode="extended"
        showDescription
        filters={
          <ProjectFilters
            projectTags={projectTags}
            onFilterValueChange={handleFilterValueChange}
            tagsToFilter={tagsToFilter}
            onOwnersFilterFocus={handleOwnersFilterFocus}
          />
        }
      />
    </div>
  );
}
