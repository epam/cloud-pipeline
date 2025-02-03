import { useParams } from 'react-router';
import { PageSpinner } from '../../shared/ui';
import { usePipelineInfo } from '../../state/pipelines/hooks';
import { PipelinePage } from './pipeline';

export const PipelinePageContainer = () => {
  const { pipelineId } = useParams();

  const { pipelineInfo, parentProject, pending, error, versions } =
    usePipelineInfo(pipelineId, true);

  if (error) {
    return <div>{error}</div>;
  }

  if (pending && !pipelineInfo) {
    return <PageSpinner />;
  }

  if (!pipelineInfo || !versions?.length) {
    return <div>No data</div>;
  }

  return (
    <PipelinePage
      pipeline={pipelineInfo}
      parentProject={parentProject}
      versions={versions}
      pending={pending}
    />
  );
};
