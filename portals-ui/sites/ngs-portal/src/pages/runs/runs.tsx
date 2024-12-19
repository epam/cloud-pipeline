import { List, ListHeader } from '@cloud-pipeline/components';
import { useAuthenticatedUserRuns } from '../../shared/hooks/use-runs-filter.ts';
import { PageSpinner } from '../../shared/ui';

export function RunsPage() {
  const {
    runs,
    error: runsError,
    pending: runsPending,
  } = useAuthenticatedUserRuns({ reloadIntervalMs: 5000 });

  if (runsError) {
    return <div>{runsError}</div>;
  }

  if (runsPending) {
    return <PageSpinner />;
  }

  if (!runs) {
    return <div>No data</div>;
  }

  return (
    <div className="flex flex-col overflow-auto">
      <ListHeader title="Runs history" className="shrink-0 border" />
      <List
        className="overflow-auto border-b border-l border-r"
        items={runs}
        render={(run) => (
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
