import type { Project } from '@cloud-pipeline/core';
import { ProjectCard } from '../cards';
import { ItemsPanel } from '../items-panel';
import cn from 'classnames';
import { memo, useCallback } from 'react';
import { usePipelines } from '../../state/pipelines/hooks.ts';
import { CubeIcon } from '@heroicons/react/24/outline';
import { CreateProjectButton } from '../modals';
import { NgsFilters, useFilteredNgsItems } from '../ngs-filters';
import {
  useProjectsStore,
  useReloadProjects,
} from '../../state/projects/hooks.ts';
import { useNgsProjectSettings } from '../../state/settings/hooks.ts';
import classNames from 'classnames';
import './projects-list.css';

type Props = {
  mode?: 'standard' | 'extended';
  withFilters?: boolean;
  showDescription?: boolean;
};

export const ProjectsList = memo(
  ({ mode = 'standard', withFilters, showDescription = false }: Props) => {
    useReloadProjects();

    const {
      data: projects,
      error: projectsLoadError,
      pending: isProjectsLoading,
    } = useProjectsStore();

    const pipelines = usePipelines();

    const getRandomPipeline = useCallback(
      () =>
        pipelines.length > 0
          ? pipelines[Math.floor(Math.random() * pipelines.length)]
          : undefined,
      [pipelines],
    );

    const settings = useNgsProjectSettings();

    const {
      filteredItems: filteredProjects,
      search,
      onSearchChanged,
      filters,
      onFiltersChanged,
      config,
      error: searchError,
    } = useFilteredNgsItems(projects, { taggedObjectSettings: settings });

    const renderItem = (item: Project, search: string, i: number) => (
      <ProjectCard
        key={String(item.id)}
        project={item}
        highlightedText={search}
        className={cn({ ['border-t']: i !== 0 })}
        mode="standard"
        lastRun={getRandomPipeline()}
        showDescription={showDescription}
      />
    );

    const pending = isProjectsLoading;
    const error = projectsLoadError ?? searchError;

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
        items={filteredProjects}
        render={renderItem}
        sliced
        virtualized={mode === 'extended'}
        search={search}
        onSearchChange={onSearchChanged}
        afterSearch={
          withFilters && (
            <NgsFilters
              filters={filters}
              onFiltersChange={onFiltersChanged}
              config={config}
            />
          )
        }
        itemKey="id"
        searchClassName={classNames({
          'py-1': mode === 'extended',
        })}
        searchInputClassName="projects-list-search"
        viewAll={
          mode === 'standard'
            ? { title: 'View all projects', link: '/projects' }
            : undefined
        }
        isItemsLoading={pending}
        errorText={error && `Error: ${error}`}
      />
    );
  },
);
