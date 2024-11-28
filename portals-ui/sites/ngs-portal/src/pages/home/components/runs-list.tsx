import type { Run } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel.tsx';
import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';

export const RunsList = () => {
  const { runs } = useAuthenticatedUserRuns({ reloadIntervalMs: 5000 });
  return (
    <ItemsPanel
      className="max-h-full list-container overflow-auto"
      title="Runs history"
      renderItem={(run: Run) => (
        <div className="px-2 py-1">
          <span>
            pipeline-{run.id}, status: {run.status}
          </span>
        </div>
      )}
      items={runs}
      sliced
      itemKey="id"
      viewAll={{ title: 'View all runs', link: '/runs' }}
    />
  );
};
