import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { message, Input, Modal, Select, Form } from 'antd';
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
import { useNgsProjectsRoot } from '../../../state/settings/hooks.ts';
import type { CreateProjectFormValues } from './form-fields.ts';
import { CreateProjectField, createProjectFieldConfig } from './form-fields.ts';

type Props = CommonProps & {
  visible: boolean;
  onCancel: () => void;
};

export const CreateProjectModal = (props: Props) => {
  const { visible, onCancel } = props;

  const [form] = Form.useForm<CreateProjectFormValues>();
  const values = Form.useWatch([], form);

  const [messageApi, contextHolder] = message.useMessage();

  const navigate = useNavigate();
  const authenticatedUser = useAuthenticatedUser();

  const [pending, setPending] = useState(false);
  const [isFormValid, setIsFormValid] = useState(false);
  const [dataStorages, setDataStorages] = useState<DataStorage[] | undefined>(
    undefined,
  );

  useEffect(() => {
    setPending(true);

    fetchAvailableDataStorages()
      .then((dataStorages) => setDataStorages(dataStorages))
      .catch(noop)
      .finally(() => {
        setPending(false);
      });
  }, []);

  useEffect(() => {
    if (visible) {
      form.resetFields();
      setIsFormValid(false);
      setPending(false);
    }
  }, [visible, form]);

  useEffect(() => {
    form
      .validateFields({ validateOnly: true })
      .then(() => setIsFormValid(true))
      .catch(() => setIsFormValid(false));
  }, [form, values]);

  useEffect(() => {
    if (
      visible &&
      dataStorages?.length &&
      authenticatedUser?.defaultStorageId
    ) {
      form.setFieldsValue({ datastorage: authenticatedUser?.defaultStorageId });
    }
  }, [authenticatedUser?.defaultStorageId, dataStorages, form, visible]);

  const reloadProjects = useReloadProjectsFn();
  const ngsProjectsRoot = useNgsProjectsRoot();

  const onOk = async (): Promise<void> => {
    const values = form.getFieldsValue();

    if (pending || !values.projectName?.length) {
      return;
    }

    const { projectName } = values;

    setPending(true);

    messageApi.open({
      key: 'register',
      type: 'loading',
      content: (
        <span>
          Creating <b>{projectName}</b> project...
        </span>
      ),
    });

    try {
      const projectResponse = await registerProject(projectName, {
        parentFolderId: ngsProjectsRoot,
      });
      const projects = await reloadProjects();

      messageApi.open({
        key: 'register',
        type: 'success',
        content: (
          <span>
            Project <b>{projectName}</b> successfully created
          </span>
        ),
        duration: 2,
      });

      onCancel();
      setPending(false);

      // not always created project gets into the list of projects, waiting for projects filter fix
      if (
        projectResponse?.id &&
        projects?.some((project) => project.id === projectResponse?.id)
      ) {
        navigate(generateProjectRoutePath(projectResponse.id));
      }
    } catch (error) {
      messageApi.open({
        key: 'register',
        type: 'error',
        content: (
          <div className="flex flex-col items-start">
            <span>
              Error creating project <b>{projectName}</b>
            </span>
            <span>
              {error instanceof Error ? error.message : String(error)}
            </span>
          </div>
        ),
        duration: 2,
      });

      setPending(false);
    }
  };

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

  const filterStorageOptions = (
    searchInput: string,
    option?: { search?: string },
  ) => {
    return (
      option?.search?.toLowerCase().includes(searchInput.toLowerCase()) ?? false
    );
  };

  const isCreateButtonDisabled = pending || !isFormValid;

  return (
    <Modal
      title="Create project"
      open={visible}
      onOk={() => void onOk()}
      onCancel={onCancel}
      okButtonProps={{
        disabled: isCreateButtonDisabled,
      }}
      okText="Create"
      width={'70vw'}
      confirmLoading={pending}
      style={{ maxWidth: 740 }}
      centered>
      {contextHolder}
      <div className="flex flex-col gap-2 py-4">
        <Form
          // onValuesChange={handleValuesChange}
          form={form}
          labelCol={{
            className: 'w-[145px] text-right break-words',
          }}
          wrapperCol={{ className: 'flex-1' }}
          className="flex flex-col">
          <Form.Item
            {...createProjectFieldConfig[CreateProjectField.ProjectName]}
            hasFeedback
            validateDebounce={500}>
            <Input placeholder="Project name" />
          </Form.Item>

          <Form.Item
            {...createProjectFieldConfig[CreateProjectField.Datastorage]}>
            <Select
              showSearch
              className="w-full overflow-hidden"
              placeholder="Select datastorage"
              filterOption={filterStorageOptions}
              options={options}
            />
          </Form.Item>
        </Form>
      </div>
    </Modal>
  );
};
