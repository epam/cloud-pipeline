import type { Pipeline } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import { PipelineCard } from './pipeline-card';
import { memo } from 'react';
import cn from 'classnames';
import NavigationDependencyOutlineIcon from '@epam/assets/icons/navigation-dependency-outline.svg?react';

type Props = {
  pipelines: Pipeline[] | undefined;
};

export const PipelinesList = memo(({ pipelines }: Props) => {
  const renderItem = (item: Pipeline, search: string, i: number) => {
    return (
      <PipelineCard
        key={item.id}
        pipeline={item}
        highlightedText={search}
        className={cn({ ['border-t']: i !== 0 })}
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
      viewAll={{ title: 'View all pipelines', link: '/pipelines' }}
    />
  );
});
