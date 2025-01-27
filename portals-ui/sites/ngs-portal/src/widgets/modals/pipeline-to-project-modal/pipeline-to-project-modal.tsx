import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Modal, Select, message } from 'antd';
import type { Pipeline } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import { useProjects } from '../../../state/projects/hooks';
import { clonePipeline } from '@cloud-pipeline/api';
import { generatePipelineRoutePath } from '../../../shared/constants/routes';

type Props = CommonProps & {
  visible: boolean;
  onCancel: () => void;
  pipeline: Pipeline;
};

export const PipelineToProjectModal = (props: Props) => {
  const { pipeline, visible, onCancel } = props;
  const [messageApi, contextHolder] = message.useMessage();
  const projects = useProjects();
  const [pending, setPending] = useState(false);
  const [spin, setSpin] = useState(false);
  const [selectedProjectId, setSelectedProjectId] = useState<number>();
  const onOk = async () => {
    if (pending || !selectedProjectId) {
      return;
    }
    setPending(true);
    setSpin(true);
    const selected = projects.find(({ id }) => id === selectedProjectId)!;
    try {
      messageApi.open({
        key: 'clone pipeline',
        type: 'loading',
        content: (
          <span>
            Adding <b>{pipeline.name}</b> to <b>selected.name</b>...
          </span>
        ),
        duration: -1,
      });
      const targetName = `${selected.name}-${pipeline.name}`;
      const cloned = await clonePipeline(pipeline.id, targetName, selected.id);
      messageApi.open({
        key: 'clone pipeline',
        type: 'success',
        content: (
          <span>
            Successfully added <b>{cloned.name}</b> to
            <b>{selected?.name}</b>.
            <Link
              className="ml-1 font-semibold truncate"
              to={generatePipelineRoutePath(cloned.id)}>
              Navigate to {cloned.name}.
            </Link>
          </span>
        ),
        duration: 8,
      });
      onCancel();
    } catch (error) {
      messageApi.open({
        key: 'clone pipeline',
        type: 'error',
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
        duration: 5,
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
      <div className="flex flex-nowrap gap-1 items-center py-4">
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
