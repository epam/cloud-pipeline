import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';
import { useProjectsStore } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import { noop } from '@cloud-pipeline/core';
import { ProjectHeader } from './components';
import { HomeIcon } from '@heroicons/react/24/outline';
import { Breadcrumb } from 'antd';
import { Link } from 'react-router-dom';
import { RoutePath, AppRoutes } from '../../shared/constants/routes';
import { ItemLayout, PageSpinner } from '../../shared/ui';

export const ProjectPage = () => {
  const { projectId } = useParams();
  const {
    projects,
    error,
    pending: isProjectsPending,
    getProjectById,
  } = useProjectsStore();
  const project = getProjectById(Number(projectId));

  const [activeTabKey, setActiveTabKey] = useState('info');

  useEffect(() => {
    if (!projects && !isProjectsPending) {
      loadProjects().then(noop).catch(noop);
    }
  }, [projects, isProjectsPending]);

  const tabs = useMemo(
    () => [
      {
        key: 'info',
        label: <span className="px-4">Info</span>,
        content: <div>Info</div>,
      },
      {
        key: 'storage',
        label: <span className="px-4">Storage</span>,
        content: <div>Storage</div>,
      },
      {
        key: 'pipelines',
        label: <span className="px-4">Pipelines</span>,
        content: <div>Pipelines</div>,
      },
      {
        key: 'history',
        label: <span className="px-4">History</span>,
        content: <div>History</div>,
      },
    ],
    [],
  );

  const activeTab = useMemo(
    () => tabs.find((tab) => tab.key === activeTabKey)!,
    [activeTabKey, tabs],
  );

  if (error) {
    return <div>{error}</div>;
  }

  if (isProjectsPending && !projects?.length) {
    return <PageSpinner />;
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
          { title: project?.name },
        ]}
      />

      <ItemLayout
        className="flex-grow"
        header={
          <ProjectHeader
            project={project}
            tabs={tabs}
            onChangeTab={setActiveTabKey}
            activeKey={activeTabKey}
          />
        }
        main={<div>{activeTab.content}</div>}
      />
    </div>
  );
};
