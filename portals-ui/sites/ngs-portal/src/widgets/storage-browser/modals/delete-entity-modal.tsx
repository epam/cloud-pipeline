import { deleteDataStorageItem } from '@cloud-pipeline/api';
import type { DataStorageItem } from '@cloud-pipeline/core';
import { message, Modal } from 'antd';
import { useCallback, useState } from 'react';

type Props = {
  isOpen: boolean;
  onClose: () => void;
  storageId: number;
  onDeleteSuccess?: () => void;
  item?: DataStorageItem;
};

export const DeleteEntityModal = ({ isOpen, onClose, item, onDeleteSuccess, storageId }: Props) => {
  const [messageApi, contextHolder] = message.useMessage();

  const [isLoading, setIsLoading] = useState(false);

  const handleDelete = useCallback(async () => {
    if (!item) {
      return;
    }

    setIsLoading(true);

    try {
      await deleteDataStorageItem(storageId, item);

      messageApi.open({
        key: 'delete-entity',
        type: 'success',
        content: (
          <span>
            {item.type} <b>{item.name}</b> was deleted
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
            Failed to delete {item.type.toLowerCase()} <b>{item.name}</b>
          </span>
        ),
      });
    } finally {
      setIsLoading(false);
      onClose();
    }
  }, [item, messageApi, onClose, onDeleteSuccess, storageId]);

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
      {item && (
        <p>
          Are you sure you want to delete
          <b> {item.name}</b>?
        </p>
      )}
    </Modal>
  );
};
