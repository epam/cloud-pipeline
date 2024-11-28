import {
  executeAllowed,
  readAllowed,
  writeAllowed,
  type Project,
} from '@cloud-pipeline/core';
import { Button } from '@epam/uui';
import { ProjectCard } from './project-card';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import cn from 'classnames';
import { memo } from 'react';

type Props = {
  projects: Project[];
};

export const ProjectsList = memo(({ projects }: Props) => {
  const renderItem = (item: Project, search: string, i: number) => {
    const { mask, id } = item;

    const accessRights = {
      read: readAllowed(mask),
      write: writeAllowed(mask),
      execute: executeAllowed(mask),
    };

    return (
      <ProjectCard
        key={id}
        project={item}
        accessRights={accessRights}
        highlightedText={search}
        className={cn({ ['border-t-2']: i !== 0 })}
      />
    );
  };

  return (
    <ItemsPanel
      className="max-h-full list-container overflow-auto"
      title="Projects"
      actions={
        <Button caption="Create project" size="24" onClick={() => null} />
      }
      items={projects}
      renderItem={renderItem}
      sliced
      search
      itemKey="id"
      viewAll={{ title: 'View all projects', link: '/projects' }}
    />
  );
});
