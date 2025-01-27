import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Modal, Select, message } from 'antd';
import { clonePipeline } from '@cloud-pipeline/api';
import type { Pipeline } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import {
  useProjects,
  useReloadProjectsFn,
} from '../../../state/projects/hooks';
import { generateProjectRoutePath } from '../../../shared/constants/routes';

type Props = CommonProps & {
  visible: boolean;
  onCancel: () => void;
  pipeline: Pipeline;
};

export const PipelineToProjectModal = (props: Props) => {
  const { pipeline, visible, onCancel } = props;
  const [messageApi, contextHolder] = message.useMessage();
  const [pending, setPending] = useState(false);
  const [spin, setSpin] = useState(false);
  const projects = useProjects();
  const reloadProjects = useReloadProjectsFn();
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
      await reloadProjects();
      messageApi.open({
        key: 'clone pipeline',
        type: 'success',
        content: (
          <span>
            Pipeline <b>{cloned.name}</b> successfully added to project
            <b>{selected?.name}</b>.
            <Link
              className="ml-1 font-semibold truncate"
              to={generateProjectRoutePath(selected.id)}>
              Open project.
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
