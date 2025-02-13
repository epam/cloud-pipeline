import { Form, Input, message, Modal } from 'antd';
import {
  capitalizedString,
  correctPath,
  DataStorageItemActions,
  DataStorageItemTypes,
  noop,
} from '@cloud-pipeline/core';
import { useCallback, useState } from 'react';
import type { UpdateDataStorageItemPayload } from '@cloud-pipeline/api';
import { updateDataStorageItem } from '@cloud-pipeline/api';
import { ROOT_PLACEHOLDER } from '../utils/navigation';

type Props = {
  createEntityType: DataStorageItemTypes | undefined;
  storageId: number;
  path: string | undefined;
  onOk: () => void;
  onCancel: () => void;
};

type FieldType = {
  name?: string;
  contents?: string;
};

type FormValues = {
  name: string;
  contents: string;
};

const NAME_VALIDATION_TEXT =
  "Name can contain only letters, digits, spaces, '_', '-', '@' and '.'.";

export default function CreateDataStorageEntityModal({
  createEntityType,
  storageId,
  path,
  onOk,
  onCancel,
}: Props) {
  const [pending, setPending] = useState(false);
  const [hasErrors, setHasErrors] = useState(false);
  const [form] = Form.useForm<FormValues>();
  const [messageApi, contextHolder] = message.useMessage();
  const onChangeForm = () => {
    void form
      .validateFields()
      .then(noop)
      .catch(noop)
      .finally(() => {
        const data = form.getFieldsError();
        const errors = data.some((item) => item.errors.length);
        if (errors !== hasErrors) {
          setHasErrors(errors);
        }
      });
  };
  const clearState = useCallback(() => {
    setPending(false);
    setHasErrors(false);
  }, []);
  const submitChanges = useCallback(async () => {
    if (!storageId || !createEntityType) {
      return;
    }
    const { name, contents = '' } = form.getFieldsValue();
    const pathToEntity =
      path && path !== ROOT_PLACEHOLDER ? `${path}/${name}` : name;
    const base64Content = contents ? btoa(contents) : '';
    const payload = [
      {
        action: DataStorageItemActions.create,
        path: correctPath(pathToEntity),
        type: createEntityType,
        ...(createEntityType === DataStorageItemTypes.file
          ? { contents: base64Content }
          : {}),
      },
    ] as UpdateDataStorageItemPayload[];
    try {
      setPending(true);
      messageApi.open({
        key: 'create',
        type: 'loading',
        content: `Creating ${createEntityType} ${name}...`,
        duration: 0,
      });
      await updateDataStorageItem(storageId, payload);
      messageApi.open({
        key: 'create',
        type: 'success',
        content: `Successfully created ${createEntityType} ${name}...`,
        duration: 4,
      });
      onOk();
    } catch (error) {
      const errorText =
        error instanceof Error
          ? error.message
          : `Failed to create ${createEntityType.toLowerCase()} ${name}.`;
      messageApi.open({
        key: 'create',
        type: 'error',
        content: errorText,
        duration: 4,
      });
      onCancel();
    } finally {
      setPending(false);
    }
  }, [createEntityType, form, messageApi, onCancel, onOk, path, storageId]);
  return (
    <div>
      {contextHolder}
      <Modal
        title={capitalizedString(`Create ${createEntityType?.toLowerCase()}`)}
        onOk={() => void submitChanges()}
        destroyOnClose
        okButtonProps={{ disabled: hasErrors || pending, loading: pending }}
        onCancel={() => {
          clearState();
          onCancel();
        }}
        onClose={clearState}
        open={!!createEntityType}>
        <Form
          form={form}
          name="basic"
          labelCol={{ span: 4 }}
          wrapperCol={{ span: 20 }}
          onChange={onChangeForm}
          clearOnDestroy
          autoComplete="off">
          <Form.Item<FieldType>
            label="Name"
            name="name"
            validateTrigger="onChange"
            rules={[
              { required: true, message: 'Name is required' },
              {
                pattern: /^[\da-zA-Z._\-@ ]+$/,
                message: NAME_VALIDATION_TEXT,
                validateTrigger: 'onChange',
              },
            ]}>
            <Input />
          </Form.Item>
          {createEntityType === DataStorageItemTypes.file ? (
            <Form.Item<FieldType> label="Content" name="contents">
              <Input.TextArea />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </div>
  );
}
