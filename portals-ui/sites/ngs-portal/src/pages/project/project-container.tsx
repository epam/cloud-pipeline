import { useParams } from 'react-router';
import { useProjectsStore } from '../../state/projects/hooks';
import { ProjectPage } from './project';
import { useEffect } from 'react';
import { noop } from '@cloud-pipeline/core';
import { loadProjects } from '../../state/projects/load-projects';
import { PageSpinner } from '../../shared/ui';

export const ProjectPageContainer = () => {
  const { projectId } = useParams();
  const {
    projects,
    error,
    pending: isProjectsPending,
    getProjectById,
  } = useProjectsStore();
  const project = getProjectById(Number(projectId));

  useEffect(() => {
    if (!projects && !isProjectsPending) {
      loadProjects().then(noop).catch(noop);
    }
  }, [projects, isProjectsPending]);

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
