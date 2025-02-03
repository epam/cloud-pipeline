import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import type { Project, Run } from '@cloud-pipeline/core';
import {
  RoutePath,
  AppRoutes,
  generateProjectRoutePath,
} from '../../../shared/constants/routes';
import { HomeIcon } from '@heroicons/react/24/solid';

export default function useRunBreadcrumbs(
  run: Run | undefined,
  parentProject: Project | undefined,
) {
  const runName = useMemo(
    () => (run?.tags?.alias ? `${run.tags.alias} (#${run.id})` : run?.id),
    [run],
  );
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
        { title: `Run ${runName}` },
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
      { title: <Link to={RoutePath[AppRoutes.RUNS]}>Runs</Link> },
      { title: `Run ${runName}` },
    ];
  }, [parentProject, runName]);
  return breadcrumbs;
}
