import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { message, Input, Modal, Select } from 'antd';
import {
  fetchAvailableDataStorages,
  registerProject,
} from '@cloud-pipeline/api';
import type { CommonProps } from '@cloud-pipeline/components';
import type { DataStorage } from '@cloud-pipeline/core';
import { noop } from '@cloud-pipeline/core';
import { useAuthenticatedUser } from '../../../state/authentication/hooks';
import { useReloadProjectsFn } from '../../../state/projects/hooks.ts';
import { generateProjectRoutePath } from '../../../shared/constants/routes.ts';
import './styles.css';

type Props = CommonProps & {
  visible: boolean;
  onCancel: () => void;
};

export const CreateProjectModal = (props: Props) => {
  const { visible, onCancel } = props;
  const [messageApi, contextHolder] = message.useMessage();
  const navigate = useNavigate();
  const authenticatedUser = useAuthenticatedUser();
  const [name, setName] = useState<string | undefined>('');
  const [defaultDataStorageId, setDefaultDataStorageId] = useState<
    number | undefined
  >();
  const [dataStorages, setDataStorages] = useState<DataStorage[] | undefined>(
    undefined,
  );
  const [pending, setPending] = useState(false);
  const [spin, setSpin] = useState(false);
  const resetState = useCallback(() => {
    setName('');
    setPending(false);
    setSpin(false);
    setDefaultDataStorageId(
      authenticatedUser?.defaultStorageId !== undefined
        ? Number(authenticatedUser.defaultStorageId)
        : undefined,
    );
  }, [authenticatedUser?.defaultStorageId]);
  useEffect(() => {
    resetState();
  }, [resetState]);
  useEffect(() => {
    setPending(true);
    fetchAvailableDataStorages()
      .then((dataStorages) => setDataStorages(dataStorages))
      .catch(noop)
      .finally(() => {
        setPending(false);
      });
  }, []);
  const reloadProjects = useReloadProjectsFn();
  const onOk = useCallback(async (): Promise<void> => {
    if (pending || !name?.length) {
      return;
    }
    setPending(true);
    setSpin(true);
    messageApi.open({
      key: 'register',
      type: 'loading',
      content: 'Creating data datastorage...',
    });
    try {
      const projectResponse = await registerProject(name);
      const projects = await reloadProjects();
      messageApi.open({
        key: 'register',
        type: 'success',
        content: 'Datastorage successfully created!',
        duration: 2,
      });
      onCancel();
      setPending(false);
      setSpin(false);
      // not always createdp roject gets into the list of projects, waiting for projects filter fix
      if (
        projectResponse?.id &&
        projects?.find((project) => project.id === projectResponse?.id)
      ) {
        navigate(generateProjectRoutePath(projectResponse.id));
      }
    } catch (error) {
      messageApi.open({
        key: 'register',
        type: 'error',
        content: (
          <div className="flex flex-col items-start">
            <b>Failed to create project {name}.</b>
            <span>
              {error instanceof Error ? error.message : String(error)}
            </span>
          </div>
        ),
        duration: 2,
      });
      setPending(false);
      setSpin(false);
    }
  }, [messageApi, name, navigate, onCancel, pending, reloadProjects]);
  const options = useMemo(() => {
    if (!dataStorages) {
      return [];
    }
    return dataStorages.map(({ id, name, pathMask }) => ({
      value: id,
      label: (
        <span>
          <b className="mr-1">{name}</b>({pathMask})
        </span>
      ),
      search: `${name} ${pathMask}`,
    }));
  }, [dataStorages]);
  return (
    <Modal
      title="Create project"
      open={visible}
      onOk={() => void onOk()}
      onCancel={onCancel}
      okButtonProps={{ disabled: pending || spin || !name }}
      okText="Create"
      width={'70vw'}
      confirmLoading={spin}
      style={{ maxWidth: 740 }}
      afterClose={resetState}
      centered>
      {contextHolder}
      <div className="flex flex-col gap-2 py-4">
        <div className="form-item">
          <span className="item-label">Name:</span>
          <Input
            placeholder="Project name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="form-item">
          <span className="item-label">Default datastorage:</span>
          <Select
            showSearch
            className="w-full overflow-hidden"
            value={dataStorages?.length ? defaultDataStorageId : undefined}
            onChange={setDefaultDataStorageId}
            placeholder="Select datastorage"
            filterOption={(searchInput, option) => {
              return (option?.search ?? '')
                .toLowerCase()
                .includes((searchInput ?? '').toLowerCase());
            }}
            options={options}
          />
        </div>
      </div>
    </Modal>
  );
};
