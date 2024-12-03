import { noop, type Project } from '@cloud-pipeline/core';
import { Button } from '@epam/uui';
import { ProjectCard } from './project-card';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import cn from 'classnames';
import { memo } from 'react';
import ActionAddFillIcon from '@epam/assets/icons/action-add-outline.svg?react';
import ActionJobFunctionOutlineIcon from '@epam/assets/icons/action-job_function-outline.svg?react';
import { useUuiContext } from '@epam/uui-core';
import { CreateProjectModal } from '../../../widgets/modals';

type Props = {
  projects: Project[] | undefined;
};

export const ProjectsList = memo(({ projects }: Props) => {
  const { uuiModals } = useUuiContext();
  const renderItem = (item: Project, search: string, i: number) => (
    <ProjectCard
      key={String(item.id)}
      project={item}
      highlightedText={search}
      className={cn({ ['border-t']: i !== 0 })}
    />
  );

  const openCreateProjectModal = () => {
    uuiModals
      .show((props) => <CreateProjectModal {...props} />)
      .then(noop)
      .catch(noop);
  };

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
          onClick={openCreateProjectModal}
        />
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
