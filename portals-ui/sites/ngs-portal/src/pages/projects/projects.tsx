import { useEffect } from 'react';
import { loadProjects } from '../../state/projects/load-projects';
import { useProjectsState } from '../../state/projects/hooks';
import { ProjectsList } from '../home/components/projects-list.tsx';
import { Spin } from 'antd';

export function ProjectsPage() {
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);

  const { projects, error, pending } = useProjectsState();

  if (error) {
    return <div>{error}</div>;
  }

  if (pending && (!projects || projects.length === 0)) {
    return (
      <div className="size-full flex items-center justify-center">
        <Spin size="large" />
      </div>
    );
  }

  if (!projects) {
    return <div>No data</div>;
  }

  //todo: search refactoring needed (see <ItemsPanel /> search)
  return (
    <div className="p-3 overflow-hidden h-full w-full">
      <ProjectsList
        projects={projects}
        mode="extended"
        showDescription
        withFilters
      />
    </div>
  );
}
