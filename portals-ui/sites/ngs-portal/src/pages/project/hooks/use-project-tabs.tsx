import { useMemo, useCallback } from 'react';
import { Markdown } from '@cloud-pipeline/components';
import type { Project } from '@cloud-pipeline/core';
import { LayoutCard } from '../../../shared/ui/item-layout/layout-card';
import { dummyDescription } from '../dummy.description';
import { ProjectPipelines, ProjectRunsList } from '../components';
import { useSearchParams } from 'react-router-dom';
import { ProjectSearchParams, ProjectTabs } from '../constants';

export const useProjectTabs = (project?: Project) => {
  const [searchParams, setSearchParams] = useSearchParams();

  const activeTabKey: ProjectTabs = useMemo(() => {
    const tab = searchParams.get(ProjectSearchParams.Tab);

    if (tab && Object.values(ProjectTabs).includes(tab as ProjectTabs)) {
      return tab as ProjectTabs;
    }

    return ProjectTabs.Info;
  }, [searchParams]);

  const handleChangeTab = useCallback(
    (key: string) => {
      // clear all other params
      const newSearchParams = new URLSearchParams();
      newSearchParams.set(ProjectSearchParams.Tab, key);
      setSearchParams(newSearchParams);
    },
    [setSearchParams],
  );

  const tabs = useMemo(
    () => [
      {
        key: ProjectTabs.Info,
        label: <span className="px-4">Info</span>,
        content: <Markdown>{dummyDescription}</Markdown>,
        aside: [
          <LayoutCard key="runs">
            <ProjectRunsList projectId={project?.id} />
          </LayoutCard>,
          <LayoutCard key="bottom">
            <div>Permissions</div>
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
        content: <ProjectRunsList projectId={project?.id} extended />,
      },
    ],
    [project],
  );

  const activeTab = useMemo(
    () => tabs.find((tab) => tab.key === activeTabKey) ?? tabs[0],
    [activeTabKey, tabs],
  );

  return {
    activeTab,
    tabs,
    handleChangeTab,
  };
};
