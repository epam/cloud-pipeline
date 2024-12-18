import { useEffect } from 'react';
import { useParams } from 'react-router';
import { useProjectsStore } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import { Breadcrumb, Spin } from 'antd';
import { Link } from 'react-router-dom';
import { HomeIcon } from '@heroicons/react/24/solid';
import { ItemLayout } from '../../shared/ui/item-layout';
import { AppRoutes, RoutePath } from '../../shared/constants/routes';

export function ProjectPage() {
  const { projectId } = useParams();
  const { projects, error, pending, getProjectById } = useProjectsStore();
  const project = getProjectById(Number(projectId));

  useEffect(() => {
    if (!projects?.length) {
      loadProjects()
        .then(() => {})
        .catch(() => {});
    }
  }, [projects?.length]);

  if (error) {
    return <div>{error}</div>;
  }

  if (pending && !projects?.length) {
    return (
      <div className="size-full flex items-center justify-center">
        <Spin size="large" />
      </div>
    );
  }

  if (!project) {
    return <div>No data</div>;
  }

  return (
    <div className="flex flex-col h-full">
      <Breadcrumb
        items={[
          {
            title: (
              <Link to="/">
                <HomeIcon className="w-5 h-5" />
              </Link>
            ),
          },
          { title: <Link to={RoutePath[AppRoutes.PROJECTS]}>Projects</Link> },
          { title: project.name },
        ]}
      />

      <ItemLayout
        className="flex-grow"
        header={<div>{project.name}</div>}
        main={<div>Main</div>}
        asideTop={<div>History</div>}
        asideBottom={<div>Permissions</div>}
      />
    </div>
  );
}
