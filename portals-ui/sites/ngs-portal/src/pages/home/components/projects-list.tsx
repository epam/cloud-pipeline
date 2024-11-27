import {
  executeAllowed,
  readAllowed,
  writeAllowed,
  type Project,
} from '@cloud-pipeline/core';
import { Button } from '@epam/uui';
import { ProjectCard } from './project-card';
import HighlightedText from '../../../shared/highlight-text';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';

type Props = {
  projects: Project[];
};

export const ProjectsList = ({ projects }: Props) => {
  const renderItem = (item: Project, search: string, i: number) => {
    const { mask, id, name, owner } = item;

    const accessRights = {
      read: readAllowed(mask),
      write: writeAllowed(mask),
      execute: executeAllowed(mask),
    };

    return (
      <ProjectCard
        key={id}
        id={id}
        name={<HighlightedText search={search}>{name}</HighlightedText>}
        owner={owner}
        hasDivider={i !== 0}
        accessRights={accessRights}
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
};
