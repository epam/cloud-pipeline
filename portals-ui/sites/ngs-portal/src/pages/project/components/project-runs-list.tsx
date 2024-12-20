import { useAuthenticatedUserRuns } from '../../../shared/hooks/use-runs-filter.ts';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel.tsx';
import type { Run } from '@cloud-pipeline/core';
import { RunCard } from '../../../widgets/cards';
import cn from 'classnames';

function runCardRenderer(item: Run, _: string, i: number) {
  return (
    <RunCard
      key={item.id}
      run={item}
      className={cn({ ['border-t']: i !== 0 })}
    />
  );
}

export function ProjectRunsList() {
  const { runs } = useAuthenticatedUserRuns({ reloadIntervalMs: 5000 });

  return (
    <ItemsPanel<Run>
      className="max-h-full bg-white overflow-auto"
      render={runCardRenderer}
      items={runs}
      sliced
      itemKey="id"
    />
  );
}
