import type { Pipeline } from '@cloud-pipeline/core';
import HighlightedText from '../../../shared/highlight-text';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import { PipelineCard } from './pipeline-card';

type Props = {
  pipelines: Pipeline[];
};

export const PipelinesList = ({ pipelines }: Props) => {
  const renderItem = (item: Pipeline, search: string, i: number) => {
    const { id, name, owner, description } = item;

    return (
      <PipelineCard
        key={id}
        id={id}
        name={<HighlightedText search={search}>{name}</HighlightedText>}
        owner={owner}
        hasDivider={i !== 0}
        description={description}
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
};
