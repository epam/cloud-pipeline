import { Spinner } from '@epam/uui-components';
import { useEffect } from 'react';
import { loadRuns } from '../../state/runs/load-runs';
import { useRunsState } from '../../state/runs/hooks';
import { useAuthenticationState } from '../../state/authentication/hooks';
import { List, ListHeader } from '@cloud-pipeline/components';

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
    <div className="flex flex-col overflow-auto">
      <ListHeader title="Runs history" className="shrink-0 border" />
      <List
        className="overflow-auto border-b border-l border-r"
        data={runs}
        renderItem={(run) => (
          <span>
            pipeline-{run.id}, status: {run.status}
          </span>
        )}
        itemKey="id"
        sliced={20}
      />
    </div>
  );
}
