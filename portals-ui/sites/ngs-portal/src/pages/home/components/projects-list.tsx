import { type Project } from '@cloud-pipeline/core';
import { Button } from '@epam/uui';
import { ProjectCard } from './project-card';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import cn from 'classnames';
import { memo } from 'react';
import ActionAddFillIcon from '@epam/assets/icons/action-add-outline.svg?react';
import ActionJobFunctionOutlineIcon from '@epam/assets/icons/action-job_function-outline.svg?react';

type Props = {
  projects: Project[] | undefined;
  mode: 'standard' | 'compact';
};

const cardCx = {
  standard: 'px-3 py-2',
  compact: 'px-2 py-1',
};

export const ProjectsList = memo(({ projects, mode }: Props) => {
  console.log(mode)
  const renderItem = (item: Project, search: string, i: number) => (
    <ProjectCard
      key={String(item.id)}
      project={item}
      highlightedText={search}
      className={cn(cardCx[mode], { ['border-t']: i !== 0 })}
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
      search
      itemKey="id"
      mode={mode}
      viewAll={{ title: 'View all projects', link: '/projects' }}
    />
  );
});
