import { type Project } from '@cloud-pipeline/core';
import { Button } from '@epam/uui';
import { ProjectCard } from './project-card';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import cn from 'classnames';
import { memo } from 'react';

type Props = {
  projects: Project[];
};

export const ProjectsList = memo(({ projects }: Props) => {
  const renderItem = (item: Project, search: string, i: number) => (
    <ProjectCard
      key={String(item.id)}
      project={item}
      highlightedText={search}
      className={cn({ ['border-t']: i !== 0 })}
    />
  );

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
