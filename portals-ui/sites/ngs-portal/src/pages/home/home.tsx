import { useEffect } from 'react';
import { useProjectsState } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import { usePipelinesState } from '../../state/pipelines/hooks.ts';
import { loadPipelines } from '../../state/pipelines/load-pipelines.ts';
import { ProjectsList, PipelinesList, RunsList } from './components';
import './style.css';

export const HomePage = () => {
  const { projects } = useProjectsState();
  const { pipelines } = usePipelinesState();

  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});

    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);

  return (
    <div className="relative flex h-full w-full gap-4 overflow-hidden flex-nowrap">
      <div className="flex-1 h-full overflow-auto">
        {projects?.length ? (
          <ProjectsList showDescription projects={projects} />
        ) : (
          <div>No data</div>
        )}
      </div>

      <div className="flex-1 h-full overflow-auto">
        {pipelines?.length ? (
          <PipelinesList showDescription pipelines={pipelines} />
        ) : (
          <div>No data</div>
        )}
      </div>

      <div className="flex-1 h-full overflow-auto">
        <RunsList />
      </div>
    </div>
  );
};
