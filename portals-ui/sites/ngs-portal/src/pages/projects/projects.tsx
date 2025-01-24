import { ProjectsList } from '../../widgets/projects-list';

export function ProjectsPage() {
  return (
    <div className="overflow-hidden h-full w-full">
      <ProjectsList mode="extended" showDescription withFilters />
    </div>
  );
}
