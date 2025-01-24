import { useParams } from 'react-router';
import { useProjectsStore } from '../../state/projects/hooks';
import { ProjectPage } from './project';
import { PageSpinner } from '../../shared/ui';

export const ProjectPageContainer = () => {
  const { projectId } = useParams();
  const {
    data: projects,
    error,
    pending: isProjectsPending,
    getProjectById,
  } = useProjectsStore();
  const project = getProjectById(Number(projectId));

  if (error) {
    return <div>{error}</div>;
  }

  if (isProjectsPending && !projects?.length) {
    return <PageSpinner />;
  }

  if (!project) {
    return <div>No data</div>;
  }

  return <ProjectPage project={project} />;
};
