import { useCallback, useMemo, useState } from 'react';
import classNames from 'classnames';
import type { CommonProps } from '@cloud-pipeline/components';
import type { Pipeline, Project } from '@cloud-pipeline/core';
import { ItemsPanel } from '../../../../widgets/items-panel';
import { useNgsFilters } from '../../../../features/ngs-filters';
import { omitClonedPipelinePrefix } from '../../../../shared/helpers';
import { ProjectPipelineCard } from './project-pipeline-card.tsx';
import { DeletePipelineModal } from './delete-pipeline-modal.tsx';

type Props = CommonProps & {
  project: Project | undefined;
};

export const ProjectPipelines = ({ project }: Props) => {
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

  const [pipelineIdToDelete, setPipelineIdToDelete] = useState<number | null>(
    null,
  );

  const onDeleteClick = useCallback((id: number) => {
    setPipelineIdToDelete(id);
  }, []);

  const onDeleteModalClose = useCallback(() => {
    setPipelineIdToDelete(null);
  }, []);

  const pipelineNameToDelete = useMemo(() => {
    const pipeline = project?.pipelines?.find(
      (p) => p.id === pipelineIdToDelete,
    );

    if (pipeline) {
      return omitClonedPipelinePrefix(pipeline, project);
    }
  }, [pipelineIdToDelete, project]);

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
        onDelete={onDeleteClick}
      />
    ),
    [filteredItems.length, onDeleteClick, project],
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

      <DeletePipelineModal
        isOpen={Boolean(pipelineIdToDelete)}
        onClose={onDeleteModalClose}
        pipelineId={pipelineIdToDelete}
        pipelineName={pipelineNameToDelete}
      />
    </div>
  );
};
