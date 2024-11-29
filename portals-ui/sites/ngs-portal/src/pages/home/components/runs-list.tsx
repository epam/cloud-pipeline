import type { Run } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel.tsx';
import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { RunCard } from './run-card.tsx';
import cn from 'classnames';
import MediaPlayOutlineOptIcon from '@epam/assets/icons/media-play-outline-opt.2.svg?react';

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
    <ItemsPanel
      className="max-h-full list-container overflow-auto"
      renderItem={runCardRenderer}
      title={
        <div className="fill-current flex flex-nowrap gap-1">
          <MediaPlayOutlineOptIcon />
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
