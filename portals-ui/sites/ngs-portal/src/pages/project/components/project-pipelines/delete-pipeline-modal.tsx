import { message, Modal } from 'antd';
import { useCallback, useState } from 'react';
import { useReloadProjectsFn } from '../../../../state/projects/hooks';
import { deletePipeline } from '@cloud-pipeline/api';

type Props = {
  isOpen: boolean;
  onClose: () => void;
  pipelineId: number | null;
  pipelineName?: string;
};

export const DeletePipelineModal = ({
  isOpen,
  onClose,
  pipelineId,
  pipelineName,
}: Props) => {
  const [messageApi, contextHolder] = message.useMessage();

  const reloadProjects = useReloadProjectsFn();
  const [isLoading, setIsLoading] = useState(false);

  const handleReloadProjects = useCallback(async () => {
    try {
      await reloadProjects();
    } catch {
      messageApi.open({
        key: 'reload-projects',
        type: 'error',
        content: <span>Failed to reload projects</span>,
      });
    }
  }, [messageApi, reloadProjects]);

  const handleDelete = useCallback(async () => {
    if (!pipelineId) {
      return;
    }

    setIsLoading(true);

    try {
      await deletePipeline(pipelineId);

      messageApi.open({
        key: 'delete-pipeline',
        type: 'success',
        content: (
          <span>
            Pipeline <b>{pipelineName}</b> was deleted
          </span>
        ),
      });

      await handleReloadProjects();
    } catch {
      messageApi.open({
        key: 'delete-pipeline',
        type: 'error',
        content: (
          <span>
            Failed to delete pipeline <b>{pipelineName}</b>
          </span>
        ),
      });
    } finally {
      setIsLoading(false);
      onClose();
    }
  }, [handleReloadProjects, messageApi, onClose, pipelineId, pipelineName]);

  const handleOk = () => {
    void handleDelete();
  };

  return (
    <Modal
      title="Delete confirmation"
      open={isOpen}
      onOk={handleOk}
      okText="Delete"
      confirmLoading={isLoading}
      okButtonProps={{ danger: true }}
      onCancel={onClose}>
      {contextHolder}
      <p>
        Are you sure you want to delete pipeline
        <b> {pipelineName ?? pipelineId}</b>?
      </p>
    </Modal>
  );
};
