import { AclClass, type Project } from '@cloud-pipeline/core';
import { ProjectHeader, ProjectPipelines, ProjectRunsList } from './components';
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
import { Markdown } from '@cloud-pipeline/components';
import { useMemo } from 'react';
import { LayoutCard } from '../../shared/ui/item-layout/layout-card';
import { dummyDescription } from './dummy.description';
import { Permissions } from '../../features/permissions';
import { useNgsTabs } from '../../shared/hooks';

type Props = {
  project: Project;
};

export const ProjectPage = ({ project }: Props) => {
  const tabs = useMemo(
    () => [
      {
        key: ProjectTabs.Info,
        label: <span className="px-4">Info</span>,
        content: <Markdown>{dummyDescription}</Markdown>,
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
        content: <div>Storage</div>,
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
    [project],
  );

  const { activeTab, handleChangeTab } = useNgsTabs({
    entityId: project.id,
    generatePath: generateProjectRoutePath,
    tabs,
  });

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
