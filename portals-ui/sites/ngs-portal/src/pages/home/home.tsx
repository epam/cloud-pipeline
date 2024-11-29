import { useEffect } from 'react';
import { useProjectsState } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import { usePipelinesState } from '../../state/pipelines/hooks.ts';
import { loadPipelines } from '../../state/pipelines/load-pipelines.ts';
import { ProjectsList, PipelinesList, RunsList } from './components';
import './style.css';

export const Home = () => {
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
    <div className="flex h-full w-full gap-1 overflow-hidden flex-nowrap p-1">
      <div className="flex-1 h-full overflow-auto p-2">
        <ProjectsList projects={projects} />
      </div>

      <div className="flex-1 h-full overflow-auto p-2">
        <PipelinesList pipelines={pipelines} />
      </div>

      <div className="flex-1 h-full overflow-auto p-2">
        <RunsList />
      </div>
    </div>
  );
};
