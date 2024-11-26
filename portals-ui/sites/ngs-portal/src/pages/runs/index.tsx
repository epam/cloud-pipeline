import { Spinner } from '@epam/uui';
import { useEffect, useState } from 'react';
import { useAuthenticationState } from '../../state/authentication/hooks';
import type { Run } from '@cloud-pipeline/core';
import { fetchRuns } from '@cloud-pipeline/api';

export default function Runs() {
  const [runs, setRuns] = useState<Run[]>([]);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(true);
  const { authenticatedUser } = useAuthenticationState();
  useEffect(() => {
    if (authenticatedUser?.userName) {
      setPending(true);
      fetchRuns({ owners: [authenticatedUser.userName] })
        .then(setRuns)
        .catch((error: Error) => setError(error.message))
        .finally(() => setPending(false));
    }
  }, [authenticatedUser]);
  if (pending) {
    return <Spinner />;
  }
  if (error) {
    return <div>{error}</div>;
  }
  if (!runs?.length) {
    return <div>No data</div>;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      {runs.map((run) => (
        <span key={run.id}>
          pipeline-{run.id}, status: {run.status}
        </span>
      ))}
    </div>
  );
}
