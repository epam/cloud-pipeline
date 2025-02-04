import { useCallback } from 'react';
import classNames from 'classnames';
import type { CommonProps } from '@cloud-pipeline/components';
import type { Pipeline, Project } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../../widgets/items-panel';
import { useNgsFilters } from '../../../../features/ngs-filters';
import { omitClonedPipelinePrefix } from '../../../../shared/helpers';
import { ProjectPipelineCard } from './project-pipeline-card.tsx';

type Props = CommonProps & {
  project: Project | undefined;
};

export const ProjectPipelines = (props: Props) => {
  const { project } = props;
  const pipelineSearch = useCallback(
    (item: Pipeline, search: string) => {
      const pipelineName = omitClonedPipelinePrefix(item, project);
      return pipelineName.toLowerCase().includes(search.toLowerCase());
    },
    [project],
  );
  const { filteredItems, onSearchChange, search } = useNgsFilters({
    items: project?.pipelines ?? [],
    withFilters: false,
    filtersToDisplay: [],
    searchCallback: pipelineSearch,
  });
  const renderItem = useCallback(
    (item: Pipeline, search: string, i: number) => (
      <ProjectPipelineCard
        className={classNames({
          ['border-t']: i !== 0,
          ['border-b']: i === filteredItems.length - 1,
        })}
        project={project}
        pipeline={item}
        search={search}
      />
    ),
    [filteredItems, project?.data],
  );
  return (
    <div className="overflow-hidden h-full w-full flex">
      <ItemsPanel
        className="max-h-full grow list-container overflow-auto"
        items={filteredItems}
        render={renderItem}
        sliced
        virtualized
        search={search}
        onSearchChange={onSearchChange}
        itemKey="id"
      />
    </div>
  );
};
