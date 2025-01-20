import { useMemo, useCallback, useEffect } from 'react';
import { Markdown } from '@cloud-pipeline/components';
import { AclClass, type Project } from '@cloud-pipeline/core';
import { LayoutCard } from '../../../shared/ui/item-layout/layout-card';
import { dummyDescription } from '../dummy.description';
import { ProjectPipelines, ProjectRunsList } from '../components';
import { useNavigate, useParams } from 'react-router-dom';
import {
  generateProjectRoutePath,
  ProjectTabs,
} from '../../../shared/constants/routes';
import { Permissions } from '../../../features/permissions';

export const useProjectTabs = (project: Project) => {
  const { tabId } = useParams();
  const navigate = useNavigate();

  useEffect(() => {
    const isValidTab =
      !tabId || Object.values(ProjectTabs).includes(tabId as ProjectTabs);

    if (!isValidTab) {
      navigate(generateProjectRoutePath(project.id, ProjectTabs.Info));
    }
  }, [navigate, project.id, tabId]);

  const handleChangeTab = useCallback(
    (key: string) => {
      navigate(generateProjectRoutePath(project.id, key as ProjectTabs));
    },
    [navigate, project.id],
  );

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

  const activeTab = useMemo(
    () => tabs.find((tab) => tab.key === tabId) ?? tabs[0],
    [tabId, tabs],
  );

  return {
    activeTab,
    tabs,
    handleChangeTab,
  };
};
