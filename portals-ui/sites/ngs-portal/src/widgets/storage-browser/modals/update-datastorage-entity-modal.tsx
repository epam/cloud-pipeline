import { Form, Input, message, Modal } from 'antd';
import {
  capitalizedString,
  correctPath,
  DataStorageItemActions,
  DataStorageItemTypes,
  noop,
} from '@cloud-pipeline/core';
import { useCallback, useEffect, useState } from 'react';
import type { UpdateDataStorageItemPayload } from '@cloud-pipeline/api';
import { updateDataStorageItem } from '@cloud-pipeline/api';
import { ROOT_PLACEHOLDER } from '../utils/navigation';
import { actionWords, NAME_VALIDATION_TEXT, UpdateEntityModalMode } from '../constants';

type Props = {
  entityType: DataStorageItemTypes | undefined;
  storageId: number;
  path: string | undefined;
  onOk: () => void;
  onCancel: () => void;
  isOpen: boolean;
  entityName?: string;
  mode?: UpdateEntityModalMode;
};

type FieldType = {
  name?: string;
  contents?: string;
};

type FormValues = {
  name: string;
  contents: string;
};

export function UpdateDataStorageEntityModal({
  entityType,
  storageId,
  path,
  onOk,
  onCancel,
  entityName,
  isOpen,
  mode = UpdateEntityModalMode.Create,
}: Props) {
  const [pending, setPending] = useState(false);
  const [hasErrors, setHasErrors] = useState(false);
  const [form] = Form.useForm<FormValues>();
  const [messageApi, contextHolder] = message.useMessage();
  const actionWord = actionWords[mode];

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

  useEffect(() => {
    form.setFieldsValue({ name: entityName });
  }, [form, mode, entityName]);

  const submitChanges = useCallback(async () => {
    if (!storageId || !entityType) {
      return;
    }
    const { name, contents = '' } = form.getFieldsValue();
    const pathToEntity = path && path !== ROOT_PLACEHOLDER ? `${path}/${name}` : name;
    const oldPathToEntity = path && path !== ROOT_PLACEHOLDER ? `${path}/${entityName}` : entityName;
    const base64Content = contents ? btoa(contents) : '';

    const action = mode === UpdateEntityModalMode.Create ? DataStorageItemActions.create : DataStorageItemActions.move;

    const payload = [
      {
        action,
        path: correctPath(pathToEntity),
        type: entityType,
        ...(entityType === DataStorageItemTypes.file ? { contents: base64Content } : {}),
        ...(mode === UpdateEntityModalMode.Update ? { oldPath: correctPath(oldPathToEntity) } : {}),
      },
    ] as UpdateDataStorageItemPayload[];

    try {
      setPending(true);

      messageApi.open({
        key: mode,
        type: 'loading',
        content: (
          <span>
            {actionWord.pending} ${entityType.toLowerCase()} <b>{name}</b>...
          </span>
        ),
        duration: 0,
      });

      await updateDataStorageItem(storageId, payload);

      messageApi.open({
        key: mode,
        type: 'success',
        content: (
          <span>
            Successfully {actionWord.success} {entityType.toLowerCase()} <b>{name}</b>.`
          </span>
        ),
        duration: 4,
      });

      onOk();
    } catch (error) {
      const errorText =
        error instanceof Error ? (
          error.message
        ) : (
          <span>
            Failed to {actionWord.error} {entityType.toLowerCase()} <b>{name}</b>.
          </span>
        );

      messageApi.open({
        key: mode,
        type: 'error',
        content: errorText,
        duration: 4,
      });

      onCancel();
    } finally {
      setPending(false);
    }
  }, [actionWord, entityName, entityType, form, messageApi, mode, onCancel, onOk, path, storageId]);

  return (
    <div>
      {contextHolder}
      <Modal
        title={capitalizedString(`${actionWord.action} ${entityType?.toLowerCase()}`)}
        onOk={() => void submitChanges()}
        destroyOnClose
        okText={actionWord.action}
        okButtonProps={{ disabled: hasErrors || pending, loading: pending }}
        onCancel={() => {
          clearState();
          onCancel();
        }}
        onClose={clearState}
        open={isOpen}>
        <Form
          form={form}
          name="basic"
          initialValues={{
            name: entityName,
          }}
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
          {entityType === DataStorageItemTypes.file && mode === UpdateEntityModalMode.Create ? (
            <Form.Item<FieldType> label="Content" name="contents">
              <Input.TextArea />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </div>
  );
}
