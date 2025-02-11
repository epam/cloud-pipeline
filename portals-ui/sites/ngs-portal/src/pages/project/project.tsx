import { AclClass, type Project } from '@cloud-pipeline/core';
import {
  ProjectDescription,
  ProjectHeader,
  ProjectPipelines,
  ProjectRunsList,
  ProjectStorage,
} from './components';
import { HomeIcon } from '@heroicons/react/24/outline';
import { Breadcrumb } from 'antd';
import { Link } from 'react-router-dom';
import {
  RoutePath,
  AppRoutes,
  ProjectTabs,
  generateProjectRoutePath,
} from '../../shared/constants/routes';
import { ItemLayout } from '../../shared/ui';
import { useMemo } from 'react';
import { LayoutCard } from '../../shared/ui/item-layout/layout-card';
import { Permissions } from '../../features/permissions';
import { useNgsTabs } from '../../shared/hooks';
import { useProjectDescription } from './hooks';

type Props = {
  project: Project;
};

export const ProjectPage = ({ project }: Props) => {
  const {
    handleDescriptionSave,
    projectDescription,
    projectDescriptionContextHolder,
  } = useProjectDescription(project);

  const tabs = useMemo(
    () => [
      {
        key: ProjectTabs.Info,
        label: <span className="px-4">Info</span>,
        content: (
          <ProjectDescription
            description={projectDescription}
            onSave={handleDescriptionSave}
          />
        ),
        aside: [
          <LayoutCard key="runs">
            <ProjectRunsList projectId={project.id} />
          </LayoutCard>,
          <LayoutCard key="bottom">
            <Permissions entityId={project?.id} aclClass={AclClass.folder} />
          </LayoutCard>,
        ],
      },
      {
        key: ProjectTabs.Storage,
        label: <span className="px-4">Storage</span>,
        content: <ProjectStorage />,
      },
      {
        key: ProjectTabs.Pipelines,
        label: <span className="px-4">Pipelines</span>,
        content: <ProjectPipelines project={project} />,
      },
      {
        key: ProjectTabs.History,
        label: <span className="px-4">History</span>,
        content: <ProjectRunsList projectId={project.id} extended />,
      },
    ],
    [handleDescriptionSave, project, projectDescription],
  );

  const { activeTab, handleChangeTab } = useNgsTabs({
    entityId: project.id,
    generatePath: generateProjectRoutePath,
    tabs,
  });

  return (
    <div className="flex flex-col h-full">
      {projectDescriptionContextHolder}
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
