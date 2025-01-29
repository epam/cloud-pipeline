import { updateProject } from '@cloud-pipeline/api';
import type { Project } from '@cloud-pipeline/core';
import { message } from 'antd';
import { useState, useCallback } from 'react';

export const useProjectDescription = (project: Project) => {
  const [messageApi, contextHolder] = message.useMessage();

  const [isDescriptionLoading, setIsDescriptionLoading] = useState(false);
  const [projectDescription, setProjectDescription] = useState(
    project.description,
  );

  const displayProjectUpdateError = useCallback(() => {
    messageApi.error('Failed to update project description');
  }, [messageApi]);

  const handleDescriptionSave = useCallback(
    async (description?: string) => {
      setIsDescriptionLoading(true);
      try {
        const updatedProject = await updateProject({
          id: project.id,
          parentId: project.parentId,
          description: description?.length ? description : undefined,
          name: project.name,
        });

        if (!updatedProject) {
          throw new Error();
        }

        if (updatedProject.description !== projectDescription) {
          setProjectDescription(updatedProject.description);
        }
      } catch {
        displayProjectUpdateError();
      } finally {
        setIsDescriptionLoading(false);
      }
    },
    [
      displayProjectUpdateError,
      project.id,
      project.name,
      project.parentId,
      projectDescription,
    ],
  );

  return {
    projectDescriptionContextHolder: contextHolder,
    isDescriptionLoading,
    projectDescription,
    handleDescriptionSave,
  };
};
