import { Spinner } from '@epam/uui-components';
import { useEffect } from 'react';
import { loadRuns } from '../../state/runs/load-runs';
import { useRunsState } from '../../state/runs/hooks';
import { useAuthenticationState } from '../../state/authentication/hooks';

export default function Runs() {
  const { runs, error: runsError, pending: runsPending } = useRunsState();
  const { authenticatedUser } = useAuthenticationState();
  useEffect(() => {
    if (authenticatedUser?.userName) {
      loadRuns({ owners: [authenticatedUser.userName] })
        .then(() => {})
        .catch(() => {});
    }
  }, [authenticatedUser]);
  if (runsError) {
    return <div>{runsError}</div>;
  }
  if (runsPending) {
    return <Spinner />;
  }
  if (!runs) {
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
