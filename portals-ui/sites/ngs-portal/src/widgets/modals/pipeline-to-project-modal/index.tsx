import { noop } from '@cloud-pipeline/core';
import {
  ModalHeader,
  ModalFooter,
  Button,
  ModalBlocker,
  ModalWindow,
  LabeledInput,
  PickerInput,
  SuccessNotification,
  ErrorNotification,
} from '@epam/uui';
import { useArrayDataSource, useUuiContext, type IModal } from '@epam/uui-core';
import { useEffect, useState } from 'react';
import CircleLoaderIcon from '@epam/assets/icons/loaders/circle-loader.svg?react';
import { loadProjects } from '../../../state/projects/load-projects';
import { useProjectsState } from '../../../state/projects/hooks';

export const PipelineToProjectModal = (props: IModal<string>) => {
  const { projects } = useProjectsState();
  const [selectedProjectId, setSelectedProjectId] = useState<number>();
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);
  const { uuiNotifications } = useUuiContext();
  const [pending, setPending] = useState(false);
  const [spin, setSpin] = useState(false);
  const dataSource = useArrayDataSource(
    {
      items: projects,
    },
    [],
  );
  const onOk = () => {
    if (pending || !selectedProjectId) {
      return;
    }
    setPending(true);
    setSpin(true);
    const selected = projects?.find(({ id }) => id === selectedProjectId);
    try {
      uuiNotifications
        .show((props) => (
          <SuccessNotification {...props}>
            <b>Successfully added to {selected?.name ?? 'project'}.</b>
          </SuccessNotification>
        ))
        .then(noop)
        .catch(noop);
      props.success('');
    } catch (error) {
      uuiNotifications
        .show((props) => (
          <ErrorNotification {...props}>
            <b>Failed to add to {selected?.name ?? 'project'}.</b>
            <span>
              {error instanceof Error ? error.message : String(error)}
            </span>
          </ErrorNotification>
        ))
        .then(noop)
        .catch(noop);
    } finally {
      setPending(false);
      setSpin(false);
    }
  };
  return (
    <ModalBlocker {...props}>
      <ModalWindow width={'70vw'} style={{ maxWidth: 740 }}>
        <ModalHeader title="Create project" onClose={() => props.abort()} />
        <div className="flex flex-col gap-2 p-4">
          <LabeledInput
            htmlFor="datastorage"
            label={<span>Select datastorage:</span>}
            labelPosition="left">
            <PickerInput
              dataSource={dataSource}
              value={selectedProjectId}
              onValueChange={setSelectedProjectId}
              selectionMode="single"
              valueType="id"
            />
          </LabeledInput>
        </div>
        <ModalFooter cx="justify-end">
          <Button
            color="secondary"
            fill="outline"
            caption="Cancel"
            onClick={() => props.abort()}
          />
          <Button
            color="primary"
            caption="Add"
            isDisabled={pending || spin}
            onClick={onOk}
            iconPosition="right"
            icon={spin ? CircleLoaderIcon : undefined}
          />
        </ModalFooter>
      </ModalWindow>
    </ModalBlocker>
  );
};
