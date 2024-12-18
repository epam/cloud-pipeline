import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';
import { useProject, useProjectsState } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import { noop } from '@cloud-pipeline/core';
import ProjectHeader from './project-header';

const ProjectPage = () => {
  const { id } = useParams();
  const { projects, pending: projectsPending } = useProjectsState();
  const project = useProject(id);
  const [activeTabKey, setActiveTabKey] = useState('info');
  useEffect(() => {
    if (!projects && !projectsPending) {
      loadProjects().then(noop).catch(noop);
    }
  }, [projects, projectsPending]);
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
  return (
    <div className="p-3 overflow-hidden flex flex-col gap-3 h-full w-full">
      <ProjectHeader
        project={project}
        tabs={tabs}
        onChangeTab={setActiveTabKey}
        activeKey={activeTabKey}
      />
      <div className="panel shadow p-3 flex flex-col grow max-h-full">
        {activeTab.content}
      </div>
    </div>
  );
};

export { ProjectPage };
