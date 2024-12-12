import { noop, type Project } from '@cloud-pipeline/core';
import { ProjectCard } from './project-card';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import cn from 'classnames';
import type { ReactNode } from 'react';
import { memo, useEffect } from 'react';
import { loadPipelines } from '../../../state/pipelines/load-pipelines';
import { usePipelinesState } from '../../../state/pipelines/hooks';
import { CubeIcon } from '@heroicons/react/24/outline';
import { CreateProjectButton } from '../../../widgets/modals';

type Props = {
  projects: Project[] | undefined;
  mode?: 'standard' | 'extended';
  filters?: ReactNode;
  showDescription?: boolean;
};

export const ProjectsList = memo(
  ({
    projects,
    mode = 'standard',
    filters,
    showDescription = false,
  }: Props) => {
    const { pipelines } = usePipelinesState();
    const getRandomPipeline = () =>
      pipelines?.[Math.floor(Math.random() * pipelines.length)];
    useEffect(() => {
      loadPipelines().then(noop).catch(noop);
    }, []);
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
        className="max-h-full list-container overflow-auto"
        title={
          <div className="fill-current flex items-center flex-nowrap gap-1">
            <CubeIcon className="w-5 h-5" />
            <span>Projects</span>
          </div>
        }
        actions={<CreateProjectButton />}
        items={projects}
        render={renderItem}
        sliced
        virtualized={mode === 'extended'}
        search
        beforeSearch={filters}
        itemKey="id"
        searchClassName={mode === 'extended' ? 'py-1' : undefined}
        viewAll={
          mode === 'standard'
            ? { title: 'View all projects', link: '/projects' }
            : undefined
        }
      />
    );
  },
);
