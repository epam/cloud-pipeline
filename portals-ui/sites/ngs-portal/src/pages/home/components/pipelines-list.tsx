import type { Pipeline } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import { PipelineCard } from './pipeline-card';
import { memo } from 'react';
import cn from 'classnames';

type Props = {
  pipelines: Pipeline[];
};

export const PipelinesList = memo(({ pipelines }: Props) => {
  const renderItem = (item: Pipeline, search: string, i: number) => {
    return (
      <PipelineCard
        key={item.id}
        pipeline={item}
        highlightedText={search}
        className={cn({ ['border-t-2']: i !== 0 })}
      />
    );
  };

  return (
    <ItemsPanel
      className="max-h-full list-container overflow-auto"
      title="Pipelines"
      items={pipelines}
      renderItem={renderItem}
      sliced
      search
      itemKey="id"
      viewAll={{ title: 'View all pipelines', link: '/pipelines' }}
    />
  );
});
