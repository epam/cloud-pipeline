import type { Run } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel.tsx';
import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { RunCard } from './run-card.tsx';
import cn from 'classnames';

export const RunsList = () => {
  const { runs } = useAuthenticatedUserRuns({ reloadIntervalMs: 5000 });

  const renderItem = (item: Run, _: string, i: number) => {
    return (
      <RunCard
        key={item.id}
        run={item}
        className={cn({ ['border-t']: i !== 0 })}
      />
    );
  };

  return (
    <ItemsPanel
      className="max-h-full list-container overflow-auto"
      title="Runs history"
      renderItem={renderItem}
      items={runs}
      sliced
      itemKey="id"
      viewAll={{ title: 'View all runs', link: '/runs' }}
    />
  );
};
