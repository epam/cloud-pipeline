import { noop, type Project } from '@cloud-pipeline/core';
import { ProjectCard } from '../cards';
import { ItemsPanel } from '../items-panel/items-panel.tsx';
import cn from 'classnames';
import { memo, useEffect } from 'react';
import { loadPipelines } from '../../state/pipelines/load-pipelines.ts';
import { usePipelinesState } from '../../state/pipelines/hooks.ts';
import { CubeIcon } from '@heroicons/react/24/outline';
import { CreateProjectButton } from '../modals';
import { projectFiltersToDisplay } from '../../pages/projects/constants.ts';
import { NgsFilters, useNgsFilters } from '../../features/ngs-filters';
import { useProjectsState } from '../../state/projects/hooks.ts';
import { loadProjects } from '../../state/projects/load-projects.ts';

type Props = {
  mode?: 'standard' | 'extended';
  withFilters?: boolean;
  showDescription?: boolean;
};

export const ProjectsList = memo(
  ({ mode = 'standard', withFilters, showDescription = false }: Props) => {
    useEffect(() => {
      loadProjects()
        .then(() => {})
        .catch(() => {});
    }, []);

    const {
      projects = [],
      error,
      pending: isProjectsLoading,
    } = useProjectsState();

    const { pipelines } = usePipelinesState();

    const getRandomPipeline = () =>
      pipelines?.[Math.floor(Math.random() * pipelines.length)];

    useEffect(() => {
      loadPipelines().then(noop).catch(noop);
    }, []);

    const { filteredItems, onSearchChange, filtersProps, search } =
      useNgsFilters({
        items: projects,
        withFilters,
        filtersToDisplay: projectFiltersToDisplay,
      });

    const renderItem = (item: Project, search: string, i: number) => (
      <ProjectCard
        key={String(item.id)}
        project={item}
        highlightedText={search}
        className={cn({ ['border-t']: i !== 0 })}
        mode={mode}
        lastRun={getRandomPipeline()}
        showDescription={showDescription}
      />
    );

    return (
      <ItemsPanel
        className="h-full list-container overflow-auto"
        title={
          <div className="min-h-6 fill-current flex items-center flex-nowrap gap-1">
            <CubeIcon className="w-5 h-5" />
            <span>Projects</span>
          </div>
        }
        actions={<CreateProjectButton />}
        items={filteredItems}
        render={renderItem}
        sliced
        virtualized={mode === 'extended'}
        search={search}
        onSearchChange={onSearchChange}
        afterSearch={
          filtersProps && (
            <NgsFilters className="flex-shrink-0 flex-wrap" {...filtersProps} />
          )
        }
        itemKey="id"
        searchClassName={mode === 'extended' ? 'py-1' : undefined}
        viewAll={
          mode === 'standard'
            ? { title: 'View all projects', link: '/projects' }
            : undefined
        }
        isItemsLoading={isProjectsLoading}
        errorText={error && `Error: ${error}`}
      />
    );
  },
);
