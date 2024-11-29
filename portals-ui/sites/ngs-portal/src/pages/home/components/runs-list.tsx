import type { Run } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel.tsx';
import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import MediaPlayOutlineOptIcon from '@epam/assets/icons/media-play-outline-opt.2.svg?react';

export const RunsList = () => {
  const { runs } = useAuthenticatedUserRuns({ reloadIntervalMs: 5000 });
  return (
    <ItemsPanel
      className="max-h-full list-container overflow-auto"
      title={
        <div className="fill-current flex flex-nowrap gap-1">
          <MediaPlayOutlineOptIcon />
          <span>Runs history</span>
        </div>
      }
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
