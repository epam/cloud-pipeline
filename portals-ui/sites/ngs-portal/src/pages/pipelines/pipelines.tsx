import { useEffect } from 'react';
import { loadPipelines } from '../../state/pipelines/load-pipelines';
import { usePipelinesState } from '../../state/pipelines/hooks';
import { PipelinesList } from '../home/components/pipelines-list.tsx';
import { PageSpinner } from '../../shared/ui';

export function PipelinesPage() {
  const { pipelines, error, pending } = usePipelinesState();

  useEffect(() => {
    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);

  if (error) {
    return <div>{error}</div>;
  }

  if (pending && (!pipelines || pipelines.length === 0)) {
    return <PageSpinner />;
  }

  if (!pipelines) {
    return <div>No data</div>;
  }

  return (
    <div className="overflow-hidden h-full w-full">
      <PipelinesList
        showDescription
        pipelines={pipelines}
        mode="extended"
        withFilters
      />
    </div>
  );
}
