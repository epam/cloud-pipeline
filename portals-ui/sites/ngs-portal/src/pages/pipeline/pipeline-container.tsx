import { useParams } from 'react-router';
import { useEffect } from 'react';
import { PageSpinner } from '../../shared/ui';
import { usePipelinesStore } from '../../state/pipelines/hooks';
import { loadPipelines } from '../../state/pipelines/load-pipelines';
import { PipelinePage } from './pipeline';

export const PipelinePageContainer = () => {
  const { pipelineId } = useParams();
  const { pipelines, error, pending, getPipelineById } = usePipelinesStore();
  const pipeline = getPipelineById(Number(pipelineId));

  useEffect(() => {
    if (!pipelines?.length) {
      loadPipelines()
        .then(() => {})
        .catch(() => {});
    }
  }, [pipelines?.length]);

  if (error) {
    return <div>{error}</div>;
  }

  if (pending && !pipelines?.length) {
    return <PageSpinner />;
  }

  if (!pipeline) {
    return <div>No data</div>;
  }

  return <PipelinePage pipeline={pipeline} />;
};
