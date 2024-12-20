import type { Run } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel.tsx';
import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { RunCard } from './run-card.tsx';
import cn from 'classnames';
import { PlayCircleIcon } from '@heroicons/react/24/outline';

function runCardRenderer(item: Run, _: string, i: number) {
  return (
    <RunCard
      key={item.id}
      run={item}
      className={cn({ ['border-t']: i !== 0 })}
    />
  );
}

export const RunsList = () => {
  const { runs } = useAuthenticatedUserRuns({ reloadIntervalMs: 5000 });

  return (
    <ItemsPanel<Run>
      className="max-h-full list-container overflow-auto"
      render={runCardRenderer}
      title={
        <div className="min-h-6 fill-current flex items-center flex-nowrap gap-1">
          <PlayCircleIcon className="w-5 h-5" />
          <span>Runs history</span>
        </div>
      }
      items={runs}
      sliced
      itemKey="id"
      viewAll={{ title: 'View all runs', link: '/runs' }}
    />
  );
};
