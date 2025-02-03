import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import type { Pipeline, Project } from '@cloud-pipeline/core';
import {
  RoutePath,
  AppRoutes,
  generateProjectRoutePath,
} from '../../../shared/constants/routes';
import { HomeIcon } from '@heroicons/react/24/solid';

export default function usePipelineBreadcrumbs(
  pipeline: Pipeline,
  parentProject?: Project,
) {
  const breadcrumbs = useMemo(() => {
    if (parentProject) {
      return [
        {
          title: (
            <Link to="/">
              <HomeIcon className="w-5 h-5" />
            </Link>
          ),
        },
        { title: <Link to={RoutePath[AppRoutes.PROJECTS]}>Projects</Link> },
        {
          title: (
            <Link to={generateProjectRoutePath(parentProject.id)}>
              {parentProject.name}
            </Link>
          ),
        },
        { title: pipeline.name },
      ];
    }
    return [
      {
        title: (
          <Link to="/">
            <HomeIcon className="w-5 h-5" />
          </Link>
        ),
      },
      { title: <Link to={RoutePath[AppRoutes.PIPELINES]}>Pipelines</Link> },
      { title: pipeline.name },
    ];
  }, [parentProject, pipeline.name]);
  return breadcrumbs;
}
