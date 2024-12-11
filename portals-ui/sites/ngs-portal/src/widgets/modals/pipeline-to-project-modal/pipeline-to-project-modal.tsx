import { useCallback, useEffect, useMemo, useState } from 'react';
import { Modal, Select, message } from 'antd';
import type { Pipeline } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import { loadProjects } from '../../../state/projects/load-projects';
import { useProjectsState } from '../../../state/projects/hooks';

type Props = CommonProps & {
  visible: boolean;
  onCancel: () => void;
  pipeline: Pipeline;
};

export const PipelineToProjectModal = (props: Props) => {
  const { pipeline, visible, onCancel } = props;
  const [messageApi, contextHolder] = message.useMessage();
  const { projects } = useProjectsState();
  const [pending, setPending] = useState(false);
  const [spin, setSpin] = useState(false);
  const [selectedProjectId, setSelectedProjectId] = useState<number>();
  useEffect(() => {
    loadProjects().then(noop).catch(noop);
  }, []);
  const onOk = () => {
    if (pending || !selectedProjectId) {
      return;
    }
    setPending(true);
    setSpin(true);
    const selected = projects?.find(({ id }) => id === selectedProjectId);
    try {
      messageApi.success({
        content: (
          <b>
            Successfully added {pipeline?.name ?? 'pipeline'} to{' '}
            {selected?.name ?? 'project'}.
          </b>
        ),
        duration: 2,
      });
      onCancel();
    } catch (error) {
      messageApi.error({
        content: (
          <div className="flex flex-col items-start">
            <b>
              Failed to add {pipeline?.name ?? 'pipeline'} to{' '}
              {selected?.name ?? 'project'}.
            </b>
            <span>
              {error instanceof Error ? error.message : String(error)}
            </span>
          </div>
        ),
        duration: 2,
      });
    } finally {
      setPending(false);
      setSpin(false);
    }
  };
  const resetState = useCallback(() => {
    setSelectedProjectId(undefined);
    setPending(false);
    setSpin(false);
  }, []);
  const options = useMemo(
    () =>
      projects?.map(({ id, name }) => ({
        value: id,
        label: name,
      })) ?? [],
    [projects],
  );
  return (
    <Modal
      title={`Add ${pipeline.name} to project`}
      open={visible}
      onOk={() => void onOk()}
      onCancel={onCancel}
      okButtonProps={{
        disabled: pending || spin || selectedProjectId === undefined,
      }}
      okText="Add"
      width={'70vw'}
      confirmLoading={spin}
      style={{ maxWidth: 600 }}
      afterClose={resetState}
      centered>
      {contextHolder}
      <div className="flex flex-nowrap gap-1 items-center">
        <span className="whitespace-nowrap">Select project:</span>
        <Select
          showSearch
          className="w-full overflow-hidden"
          value={selectedProjectId}
          onChange={setSelectedProjectId}
          placeholder="Select project"
          optionFilterProp="label"
          options={options}
        />
      </div>
    </Modal>
  );
};
