import { PipelinesList } from '../../widgets/pipelines-list';

export function PipelinesPage() {
  return (
    <div className="overflow-hidden h-full w-full">
      <PipelinesList showDescription mode="extended" withFilters />
    </div>
  );
}
