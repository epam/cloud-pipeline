import { useCallback, useState } from 'react';
import classNames from 'classnames';
import { PencilIcon } from '@heroicons/react/24/outline';
import type { CommonProps } from '@cloud-pipeline/components';
import type { Project } from '@cloud-pipeline/core';
import { EditProjectTagsModal } from './edit-project-tags-modal.tsx';

type EditProjectTagsButtonProps = CommonProps & {
  project: Project;
};

export const EditProjectTagsButton = (props: EditProjectTagsButtonProps) => {
  const { className, project } = props;
  const [visible, setVisible] = useState(false);
  const showModal = useCallback(() => {
    setVisible(true);
  }, []);
  const hideModal = useCallback(() => {
    setVisible(false);
  }, []);
  return (
    <>
      <span
        className={classNames(
          className,
          'cursor-pointer flex flex-nowrap items-center gap-1 text-xs text-link',
        )}
        onClick={showModal}>
        <PencilIcon className="w-4 h-4" />
        <span>Edit Tags</span>
      </span>
      <EditProjectTagsModal
        project={project}
        onCancel={hideModal}
        visible={visible}
      />
    </>
  );
};
