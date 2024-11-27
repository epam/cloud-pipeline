import { useEffect } from 'react';
import type { Run } from '@cloud-pipeline/core';
import { Button } from '@epam/uui';
import { useProjectsState } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import HighlightedText from '../../shared/highlight-text';
import { ItemsPanel } from '../../widgets/items-panel/items-panel.tsx';
import { usePipelinesState } from '../../state/pipelines/hooks.ts';
import { loadPipelines } from '../../state/pipelines/load-pipelines.ts';
import './style.css';

export const Home = () => {
  const { projects } = useProjectsState();
  const { pipelines } = usePipelinesState();
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);
  useEffect(() => {
    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);
  return (
    <div className="flex h-full w-full gap-1 overflow-hidden flex-nowrap p-1">
      <div className="flex-1 h-full overflow-auto p-2">
        <ItemsPanel
          className="max-h-full list-container overflow-auto"
          title="Projects"
          actions={
            <Button caption="Create project" size="24" onClick={() => null} />
          }
          items={projects}
          renderItem={(item, search) => (
            <div className="p-2 border-b">
              <HighlightedText search={search}>{item.name}</HighlightedText>
            </div>
          )}
          sliced
          search
          itemKey="id"
          viewAll={{ title: 'View all projects', link: '/projects' }}
        />
      </div>
      <div className="flex-1 h-full overflow-auto p-2">
        <ItemsPanel
          className="max-h-full list-container overflow-auto"
          title="Pipelines"
          items={pipelines}
          renderItem={(item, search) => (
            <div className="p-2 border-b">
              <HighlightedText search={search}>{item.name}</HighlightedText>
            </div>
          )}
          sliced
          search
          itemKey="id"
          viewAll={{ title: 'View all pipelines', link: '/pipelines' }}
        />
      </div>
      <div className="flex-1 h-full overflow-auto p-2">
        <ItemsPanel
          className="max-h-full list-container overflow-auto"
          title="Runs history"
          renderItem={(run: Run) => (
            <div className="p-2 border-b">
              <span>
                pipeline-{run.id}, status: {run.status}
              </span>
            </div>
          )}
          sliced
          itemKey="id"
          viewAll={{ title: 'View all runs', link: '/runs' }}
        />
      </div>
    </div>
  );
};
