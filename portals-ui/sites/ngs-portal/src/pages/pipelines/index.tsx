import { useEffect } from 'react';
import { Spinner } from '@epam/uui';
import { loadPipelines } from '../../state/pipelines/load-pipelines';
import { usePipelinesState } from '../../state/pipelines/hooks';
import { PipelinesList } from '../home/components/pipelines-list.tsx';

export default function PipelinesPage() {
  const { pipelines, error, pending } = usePipelinesState();
  useEffect(() => {
    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);
  if (error) {
    return <div>{error}</div>;
  }
  if (pending) {
    return <Spinner />;
  }
  if (!pipelines) {
    return <div>No data</div>;
  }
  return <PipelinesList pipelines={pipelines} mode="extended" />;
}
