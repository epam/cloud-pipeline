import { useState, useMemo } from 'react';
import { Markdown } from '@cloud-pipeline/components';
import type { Project } from '@cloud-pipeline/core';
import { LayoutCard } from '../../../shared/ui/item-layout/layout-card';
import { dummyDescription } from '../dummy.description';
import {
  ProjectPipelines,
  ProjectPermissions,
  ProjectRunsList,
} from '../components';

export const useProjectTabs = (project?: Project) => {
  const [activeTabKey, setActiveTabKey] = useState('info');

  const tabs = useMemo(
    () => [
      {
        key: 'info',
        label: <span className="px-4">Info</span>,
        content: <Markdown>{dummyDescription}</Markdown>,
        aside: [
          <LayoutCard key="runs">
            <ProjectRunsList />
          </LayoutCard>,
          <LayoutCard key="bottom">
            <ProjectPermissions projectId={project?.id} />
          </LayoutCard>,
        ],
      },
      {
        key: 'storage',
        label: <span className="px-4">Storage</span>,
        content: <div>Storage</div>,
      },
      {
        key: 'pipelines',
        label: <span className="px-4">Pipelines</span>,
        content: <ProjectPipelines project={project} />,
      },
      {
        key: 'history',
        label: <span className="px-4">History</span>,
        content: <div>History</div>,
      },
    ],
    [project],
  );

  const activeTab = useMemo(
    () => tabs.find((tab) => tab.key === activeTabKey)!,
    [activeTabKey, tabs],
  );

  return {
    activeTab,
    tabs,
    handleChangeTab: setActiveTabKey,
  };
};
