import { useEffect } from 'react';
import type { Run } from '@cloud-pipeline/core';
import { useProjectsState } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import { ItemsPanel } from '../../widgets/items-panel/items-panel.tsx';
import { usePipelinesState } from '../../state/pipelines/hooks.ts';
import { loadPipelines } from '../../state/pipelines/load-pipelines.ts';
import './style.css';
import { ProjectsList, PipelinesList } from './components';

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
      {projects && (
        <div className="flex-1 h-full overflow-auto p-2">
          <ProjectsList projects={projects} />
        </div>
      )}

      {pipelines && (
        <div className="flex-1 h-full overflow-auto p-2">
          <PipelinesList pipelines={pipelines} />
        </div>
      )}

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
