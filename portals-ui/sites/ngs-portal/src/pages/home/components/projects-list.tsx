import { type Project } from '@cloud-pipeline/core';
import { Button } from '@epam/uui';
import { ProjectCard } from './project-card';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import cn from 'classnames';
import type { ReactNode } from 'react';
import { memo, useEffect, useMemo } from 'react';
import ActionAddFillIcon from '@epam/assets/icons/action-add-outline.svg?react';
import ActionJobFunctionOutlineIcon from '@epam/assets/icons/action-job_function-outline.svg?react';
import { loadPipelines } from '../../../state/pipelines/load-pipelines';
import { usePipelinesState } from '../../../state/pipelines/hooks';

type Props = {
  projects: Project[] | undefined;
  mode?: 'standard' | 'extended';
  filters?: ReactNode;
};

export const ProjectsList = memo(
  ({ projects, mode = 'standard', filters }: Props) => {
    const { pipelines } = usePipelinesState();
    const getRandomPipeline = () =>
      pipelines?.[Math.floor(Math.random() * pipelines.length)];
    useEffect(() => {
      loadPipelines()
        .then(() => {})
        .catch(() => {});
    }, []);
    const renderItem = (item: Project, search: string, i: number) => (
      <ProjectCard
        key={String(item.id)}
        project={item}
        highlightedText={search}
        className={cn({ ['border-t']: i !== 0 })}
        mode={mode}
        lastRun={getRandomPipeline()}
      />
    );

    return (
      <ItemsPanel
        className="max-h-full list-container overflow-auto"
        title={
          <div className="fill-current flex flex-nowrap gap-1">
            <ActionJobFunctionOutlineIcon />
            <span>Projects</span>
          </div>
        }
        actions={
          <Button
            icon={ActionAddFillIcon}
            caption="Create project"
            size="24"
            onClick={() => null}
          />
        }
        items={projects}
        renderItem={renderItem}
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
