import {Button, Form, Input, Modal} from 'antd';
import {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';
import {useCallback, MouseEvent, KeyboardEvent, useState, useEffect} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {updateFolder} from '../../../../../api';
import {folderKeys, folderQueryOptions, libraryTreeKeys} from '../../../../../queries';
import {useInvalidateDetailQueryOnOpen} from '../../base/hooks.ts';

function preventDefault(event?: MouseEvent | KeyboardEvent) {
  if (event) {
    event.stopPropagation();
    event.preventDefault();
  }
}

function FolderEditModal(props: ActionModalBaseProps & {folderId: number | undefined}) {
  const {className, style, folderId, open, onClose, disabled} = props;
  const queryClient = useQueryClient();
  const isNewFolder = folderId === undefined;
  useInvalidateDetailQueryOnOpen(open, folderKeys.detail, folderId);
  const {data: folder, isFetching: pending} = useQuery(
    folderQueryOptions(folderId, {
      enabled: !isNewFolder && open,
    }),
  );
  const [form] = Form.useForm<{name: string}>();
  useEffect(() => {
    form.setFieldValue('name', folder?.name ?? '');
  }, [folder, form]);
  const values = Form.useWatch([], form);
  const [submittable, setSubmittable] = useState(false);
  const [updatePending, setUpdatePending] = useState(false);
  const {parentId} = folder ?? {};

  useEffect(() => {
    form
      .validateFields({validateOnly: true})
      .then(() => setSubmittable(true))
      .catch(() => setSubmittable(false));
  }, [form, values]);

  const onCloseWrapper = useCallback(
    (event: MouseEvent | KeyboardEvent) => {
      preventDefault(event);
      if (onClose) {
        onClose(event);
      }
    },
    [onClose],
  );
  const onOkWrapper = useCallback(
    async (event: MouseEvent | KeyboardEvent) => {
      preventDefault(event);
      if (folderId) {
        try {
          setUpdatePending(true);
          await form.validateFields();
          const {name: folderName} = form.getFieldsValue();
          await updateFolder({
            id: folderId,
            parentId,
            name: folderName,
          });
          await Promise.all([
            queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
            queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)}),
            parentId
              ? queryClient.invalidateQueries({queryKey: folderKeys.detail(parentId)})
              : Promise.resolve(),
          ]);
          onClose?.(event);
        } catch {
          // keep modal open when validation fails
        } finally {
          setUpdatePending(false);
        }
      }
    },
    [form, folderId, parentId, setUpdatePending, onClose, queryClient],
  );
  return (
    <Modal
      destroyOnHidden
      className={className}
      style={style}
      open={open}
      onCancel={onCloseWrapper}
      footer={
        <div className="flex items-center justify-end gap-1">
          <Button disabled={updatePending || pending || disabled} onClick={onCloseWrapper}>
            Cancel
          </Button>
          <Button
            disabled={updatePending || pending || disabled || !submittable}
            onClick={onOkWrapper}
          >
            Ok
          </Button>
        </div>
      }
      title={isNewFolder ? 'Create folder' : 'Rename folder'}
    >
      <Form
        form={form}
        className="w-full h-full"
        onClick={preventDefault}
        initialValues={{name: folder?.name ?? ''}}
      >
        <Form.Item
          label="Name"
          name="name"
          validateTrigger="onChange"
          rules={[{required: true, message: 'Name is required'}]}
        >
          <Input disabled={updatePending || pending || disabled} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

export {FolderEditModal};
