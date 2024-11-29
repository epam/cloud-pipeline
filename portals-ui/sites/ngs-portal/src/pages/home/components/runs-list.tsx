import type { Run } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel.tsx';
import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { RunCard } from './run-card.tsx';
import cn from 'classnames';
import MediaPlayOutlineOptIcon from '@epam/assets/icons/media-play-outline-opt.2.svg?react';

const cardCx = {
  standard: 'px-3 py-2',
  compact: 'px-2 py-1',
};

type Props = {
  mode: 'standard' | 'compact';
};

export const RunsList = (props: Props) => {
  const { mode = 'compact' } = props;
  const { runs } = useAuthenticatedUserRuns({ reloadIntervalMs: 5000 });

  const renderItem = (item: Run, _: string, i: number) => {
    return (
      <RunCard
        key={item.id}
        run={item}
        className={cn(cardCx[mode], { ['border-t']: i !== 0 })}
      />
    );
  };

  return (
    <ItemsPanel
      className="max-h-full list-container overflow-auto"
      renderItem={renderItem}
      title={
        <div className="fill-current flex flex-nowrap gap-1">
          <MediaPlayOutlineOptIcon />
          <span>Runs history</span>
        </div>
      }
      items={runs}
      sliced
      itemKey="id"
      mode={props.mode}
      viewAll={{ title: 'View all runs', link: '/runs' }}
    />
  );
};
