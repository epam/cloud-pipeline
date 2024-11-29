import type { Pipeline } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import { PipelineCard } from './pipeline-card';
import { memo } from 'react';
import cn from 'classnames';
import NavigationDependencyOutlineIcon from '@epam/assets/icons/navigation-dependency-outline.svg?react';

type Props = {
  pipelines: Pipeline[] | undefined;
  mode: 'standard' | 'compact';
};

const cardCx = {
  standard: 'px-3 py-2',
  compact: 'px-2 py-1',
};

export const PipelinesList = memo(({ pipelines, mode }: Props) => {
  const renderItem = (item: Pipeline, search: string, i: number) => {
    return (
      <PipelineCard
        key={item.id}
        pipeline={item}
        highlightedText={search}
        className={cn(cardCx[mode], { ['border-t']: i !== 0 })}
      />
    );
  };

  return (
    <ItemsPanel
      className="max-h-full list-container overflow-auto"
      title={
        <div className="fill-current flex flex-nowrap gap-1">
          <span className="rotate-90">
            <NavigationDependencyOutlineIcon />
          </span>
          <span>Pipelines</span>
        </div>
      }
      items={pipelines}
      renderItem={renderItem}
      sliced
      search
      itemKey="id"
      mode={mode}
      viewAll={{ title: 'View all pipelines', link: '/pipelines' }}
    />
  );
});
