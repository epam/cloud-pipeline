import { useEffect } from 'react';
import { Spinner } from '@epam/uui';
import { loadPipelines } from '../../state/pipelines/load-pipelines';
import { usePipelinesState } from '../../state/pipelines/hooks';

export default function Pipelines() {
  useEffect(() => {
    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);
  const { pipelines, error, pending } = usePipelinesState();
  if (error || !pipelines) {
    return <div>{error}</div>;
  }
  if (pending) {
    return <Spinner />;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      {pipelines.map((pipeline) => (
        <span key={pipeline.id}>{pipeline.name}</span>
      ))}
    </div>
  );
}
