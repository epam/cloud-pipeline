import { Modal, Tabs } from 'antd';
import type { SelectedTask } from '../../../types';
import { useMemo, useState } from 'react';
import { RunTaskDetailsContentType } from '@cloud-pipeline/core';
import { useTaskDetails } from '../../../hooks';
import { TaskDetailsModalContent } from './task-details-modal-content';
import '../style.css';

type Props = {
  onClose: () => void;
  isOpen: boolean;
  task?: SelectedTask;
};

export const TaskDetailsModal = ({ onClose, isOpen, task }: Props) => {
  const [activeTab, setActiveTab] = useState<RunTaskDetailsContentType>(RunTaskDetailsContentType.Command);

  const { content, detailsError, isDetailsLoading } = useTaskDetails({ hash: task?.taskKey, contentType: activeTab });

  const tabs = useMemo(
    () => [
      {
        key: RunTaskDetailsContentType.Command,
        label: <span className="px-4">Command</span>,
      },
      {
        key: RunTaskDetailsContentType.Trace,
        label: <span className="px-4">Metrics</span>,
      },
      {
        key: RunTaskDetailsContentType.Log,
        label: <span className="px-4">Task Log</span>,
      },
    ],
    [],
  );

  return (
    <Modal
      title={task?.taskName}
      open={isOpen}
      onCancel={onClose}
      footer={null}
      width="90vw"
      className="task-details-modal"
      centered>
      <Tabs
        className="mb-4"
        items={tabs}
        size="middle"
        tabBarStyle={{ fontWeight: 'bold', marginBottom: 0 }}
        onChange={(key) => setActiveTab(key as RunTaskDetailsContentType)}
        activeKey={activeTab}
        tabBarGutter={0}
      />
      <TaskDetailsModalContent type={activeTab} content={content} error={detailsError} isLoading={isDetailsLoading} />
    </Modal>
  );
};
