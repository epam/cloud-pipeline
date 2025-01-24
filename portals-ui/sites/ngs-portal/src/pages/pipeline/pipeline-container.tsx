import { useParams } from 'react-router';
import { PageSpinner } from '../../shared/ui';
import { usePipelineInfo } from '../../state/pipelines/hooks';
import { PipelinePage } from './pipeline';

export const PipelinePageContainer = () => {
  const { pipelineId } = useParams();

  const { pipelineInfo, pending, error, versions } =
    usePipelineInfo(pipelineId);

  if (error) {
    return <div>{error}</div>;
  }

  if (pending && !pipelineInfo) {
    return <PageSpinner />;
  }

  if (!pipelineInfo || !versions?.length) {
    return <div>No data</div>;
  }

  return <PipelinePage pipeline={pipelineInfo} versions={versions} />;
};
