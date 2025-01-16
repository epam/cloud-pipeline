import type { Project } from '@cloud-pipeline/core';
import { ProjectHeader } from './components';
import { HomeIcon } from '@heroicons/react/24/outline';
import { Breadcrumb } from 'antd';
import { Link } from 'react-router-dom';
import { RoutePath, AppRoutes } from '../../shared/constants/routes';
import { ItemLayout } from '../../shared/ui';
import { useProjectTabs } from './hooks';

type Props = {
  project: Project;
};

export const ProjectPage = ({ project }: Props) => {
  const { activeTab, tabs, handleChangeTab } = useProjectTabs(project);

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
