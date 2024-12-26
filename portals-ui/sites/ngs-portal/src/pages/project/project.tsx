import { useEffect } from 'react';
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
import { useProjectTabs } from './hooks';

export const ProjectPage = () => {
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

  const { activeTab, tabs, handleChangeTab } = useProjectTabs(project);

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
        classes={{ content: 'overflow-hidden' }}
        header={
          <ProjectHeader
            project={project}
            tabs={tabs}
            onChangeTab={handleChangeTab}
            activeKey={activeTab.key}
          />
        }
        main={activeTab.content}
        aside={activeTab.aside}
      />
    </div>
  );
};
