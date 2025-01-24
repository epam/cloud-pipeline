import { ProjectsList } from '../../widgets/projects-list';
import { PipelinesList } from '../../widgets/pipelines-list';
import { RunsList } from '../../widgets/runs-list';
import './style.css';

export const HomePage = () => {
  return (
    <div className="relative flex h-full w-full gap-4 overflow-hidden flex-nowrap">
      <div className="flex-1 h-full overflow-auto">
        <ProjectsList showDescription />
      </div>

      <div className="flex-1 h-full overflow-auto">
        <PipelinesList showDescription />
      </div>

      <div className="flex-1 h-full overflow-auto">
        <RunsList />
      </div>
    </div>
  );
};
