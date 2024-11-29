import { useEffect, useState } from 'react';
import { useProjectsState } from '../../state/projects/hooks';
import { loadProjects } from '../../state/projects/load-projects';
import { usePipelinesState } from '../../state/pipelines/hooks.ts';
import { loadPipelines } from '../../state/pipelines/load-pipelines.ts';
import { ProjectsList, PipelinesList, RunsList } from './components';
import './style.css';

export const Home = () => {
  const { projects } = useProjectsState();
  const { pipelines } = usePipelinesState();

  const [mode, setMode] = useState<'standard' | 'compact'>('compact');

  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});

    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);

  const toggleMode = () => {
    const toggler = {
      standard: 'compact',
      compact: 'standard',
    } as Record<string, 'standard' | 'compact'>;
    setMode(toggler[mode]);
  };

  return (
    <div className="relative flex h-full w-full gap-1 overflow-hidden flex-nowrap p-1">
      <div className="flex-1 h-full overflow-auto p-2">
        <ProjectsList mode={mode} projects={projects} />
      </div>

      <div className="flex-1 h-full overflow-auto p-2">
        <PipelinesList mode={mode} pipelines={pipelines} />
      </div>

      <div className="flex-1 h-full overflow-auto p-2">
        <RunsList mode={mode} />
      </div>
      <div
        onClick={toggleMode}
        className="text-faded"
        style={{
          position: 'absolute',
          cursor: 'pointer',
          top: '97%',
        }}>
        +
      </div>
    </div>
  );
};
