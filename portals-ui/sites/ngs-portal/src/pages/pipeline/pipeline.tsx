import { useEffect } from 'react';
import { useParams } from 'react-router';
import { Breadcrumb } from 'antd';
import { Link } from 'react-router-dom';
import { HomeIcon } from '@heroicons/react/24/solid';
import { ItemLayout, PageSpinner } from '../../shared/ui';
import { AppRoutes, RoutePath } from '../../shared/constants/routes';
import { usePipelinesStore } from '../../state/pipelines/hooks';
import { loadPipelines } from '../../state/pipelines/load-pipelines';

export function PipelinePage() {
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

  return (
    <div className="flex flex-col h-full">
      <Breadcrumb
        items={[
          {
            title: (
              <Link to="/">
                <HomeIcon className="w-5 h-5" />
              </Link>
            ),
          },
          { title: <Link to={RoutePath[AppRoutes.PIPELINES]}>Pipelines</Link> },
          { title: pipeline.name },
        ]}
      />

      <ItemLayout
        header={<div>{pipeline.name}</div>}
        main={<div>Main</div>}
        asideTop={<div>Recent Runs</div>}
      />
    </div>
  );
}
