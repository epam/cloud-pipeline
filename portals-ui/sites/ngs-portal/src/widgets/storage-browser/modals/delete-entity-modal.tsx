import { deleteDataStorageItem } from '@cloud-pipeline/api';
import type { DataStorageItemTypes } from '@cloud-pipeline/core';
import { message, Modal } from 'antd';
import { useCallback, useState } from 'react';

type Props = {
  isOpen: boolean;
  onClose: () => void;
  entityName: string;
  storageId: number;
  onDeleteSuccess?: () => void;
  path?: string;
  entityType?: DataStorageItemTypes;
};

export const DeleteEntityModal = ({
  isOpen,
  onClose,
  entityName,
  entityType,
  path,
  onDeleteSuccess,
  storageId,
}: Props) => {
  const [messageApi, contextHolder] = message.useMessage();

  const [isLoading, setIsLoading] = useState(false);

  const handleDelete = useCallback(async () => {
    if (!path || !entityType) {
      return;
    }

    setIsLoading(true);

    try {
      await deleteDataStorageItem(storageId, { path, type: entityType });

      messageApi.open({
        key: 'delete-entity',
        type: 'success',
        content: (
          <span>
            {entityType} <b>{entityName}</b> was deleted
          </span>
        ),
      });

      onDeleteSuccess?.();
    } catch {
      messageApi.open({
        key: 'delete-entity',
        type: 'error',
        content: (
          <span>
            Failed to delete {entityType.toLowerCase()} <b>{entityName}</b>
          </span>
        ),
      });
    } finally {
      setIsLoading(false);
      onClose();
    }
  }, [entityName, entityType, messageApi, onClose, onDeleteSuccess, path, storageId]);

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
        Are you sure you want to delete
        <b> {entityName}</b>?
      </p>
    </Modal>
  );
};
