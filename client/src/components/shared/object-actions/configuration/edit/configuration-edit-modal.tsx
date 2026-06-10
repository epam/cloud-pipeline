import {Button, Form, Input, message, Modal, Spin, Tabs} from 'antd';
import {useCallback, useEffect, useRef, useState} from 'react';
import type {MouseEvent, KeyboardEvent} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {useAuthenticatedUser} from '../../../../../stores/users/hooks.ts';
import {
  configurationKeys,
  configurationQueryOptions,
  folderKeys,
  libraryTreeKeys,
} from '../../../../../queries';
import {saveConfiguration} from '../../../../../api/configuration/configuration-api.ts';
import roleModel from '../../../../../utils/roleModel';
import {getErrorDescription} from '../../../../../utilities/errors.ts';
import {useInvalidateDetailQueryOnOpen} from '../../base/hooks.ts';
import {ConfigurationPermissionsTab} from './configuration-permissions-tab.tsx';
import type {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';
import {ConfigurationRemoveButton} from '../remove/configuration-remove-button.tsx';

const FORM_ITEM_LAYOUT = {
  labelCol: {xs: {span: 24}, sm: {span: 8}},
  wrapperCol: {xs: {span: 24}, sm: {span: 16}},
};

type ConfigurationFormValues = {
  name: string;
  description: string;
};

type ConfigurationEditModalNewProps = {
  parentFolderId?: number;
};

type ConfigurationEditModalExistingProps = {
  configurationId: number;
};

type ConfigurationEditProps = Partial<
  ConfigurationEditModalNewProps & ConfigurationEditModalExistingProps
>;

export type ConfigurationEditModalProps = ActionModalBaseProps &
  ConfigurationEditProps & {
    pipelineTemplateId?: string;
    onDone?: () => void;
  };

function isExistingProps(
  props: ConfigurationEditProps,
): props is ConfigurationEditModalExistingProps {
  return 'configurationId' in props && typeof props.configurationId === 'number';
}

function ConfigurationEditModal({
  configurationId,
  parentFolderId: _parentFolderId,
  pipelineTemplateId,
  open = false,
  onClose,
  onDone,
}: ConfigurationEditModalProps) {
  const isNew = !isExistingProps({configurationId});

  const queryClient = useQueryClient();
  useInvalidateDetailQueryOnOpen(open, configurationKeys.detail, configurationId);
  const {
    data: configuration,
    isFetching: loadPending,
    isSuccess: loaded,
  } = useQuery({
    ...configurationQueryOptions(configurationId, {
      enabled: !isNew && open,
    }),
  });
  const configurationLoaded = isNew || loaded;
  const parentFolderId = isNew ? _parentFolderId : (configuration?.parent?.id ?? undefined);

  const [form] = Form.useForm<ConfigurationFormValues>();
  const [activeTab, setActiveTab] = useState('info');
  const [submitPending, setSubmitPending] = useState(false);
  const nameInputRef = useRef<HTMLInputElement | null>(null);

  const user = useAuthenticatedUser();
  const isConfigManager =
    user.admin || (user.roles ?? []).some((r) => r.name === 'ROLE_CONFIGURATION_MANAGER');

  const writeAllowed = isNew ? true : roleModel.writeAllowed(configuration);
  const canDelete = !isNew && !!configuration && writeAllowed && isConfigManager;
  const canSave = isNew ? isConfigManager : writeAllowed;

  const pending = loadPending || submitPending;

  useEffect(() => {
    if (open) {
      form.setFieldsValue({
        name: configuration?.name ?? '',
        description: configuration?.description ?? '',
      });
      setActiveTab('info');
    }
  }, [open, configuration, form]);

  useEffect(() => {
    if (open && nameInputRef.current) {
      setTimeout(() => {
        nameInputRef.current?.focus();
      }, 0);
    }
  }, [open]);

  const handleAfterClose = useCallback(() => {
    form.resetFields();
    setActiveTab('info');
  }, [form]);

  const handleSubmit = useCallback(
    async (e: MouseEvent | KeyboardEvent) => {
      e.preventDefault();
      let hide = message.loading(<span>Validating...</span>);
      try {
        setSubmitPending(true);
        await form.validateFields();
        hide();
        const raw = form.getFieldsValue();
        hide = message.loading(
          <span>
            {isNew ? 'Creating' : 'Updating'} <b>{raw.name}</b>...
          </span>,
          5,
        );
        await saveConfiguration({
          id: isNew ? undefined : configurationId,
          name: raw.name,
          description: raw.description,
          parentId: isNew ? parentFolderId : (configuration?.parent?.id ?? parentFolderId),
          entries: isNew ? undefined : configuration?.entries?.map((entry) => entry),
        });
        if (!isNew && configurationId !== undefined) {
          await queryClient.invalidateQueries({
            queryKey: configurationKeys.detail(configurationId),
          });
        }
        const folderId = parentFolderId ?? configuration?.parent?.id;
        await Promise.all([
          queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
          folderId
            ? queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)})
            : Promise.resolve(),
        ]);
        onDone?.();
        onClose?.(e);
      } catch (error) {
        message.error(
          <span>
            Error {isNew ? 'creating' : 'updating'} configuration: {getErrorDescription(error)}
          </span>,
          5,
        );
      } finally {
        hide();
        setSubmitPending(false);
      }
    },
    [form, isNew, configurationId, parentFolderId, configuration, queryClient, onDone, onClose],
  );

  const modalTitle = isNew
    ? pipelineTemplateId
      ? `Create configuration (${pipelineTemplateId})`
      : 'Create configuration'
    : 'Edit configuration info';

  const footer =
    activeTab !== 'info' || pending ? (
      false
    ) : (
      <div className="flex items-center justify-between w-full">
        <div>
          {canDelete && configuration && (
            <ConfigurationRemoveButton
              disabled={pending}
              id="edit-configuration-form-delete-button"
              configuration={configuration}
              onRemove={onClose}
            >
              DELETE
            </ConfigurationRemoveButton>
          )}
        </div>
        <div className="flex items-center gap-1">
          <Button disabled={pending} id="edit-configuration-form-cancel-button" onClick={onClose}>
            CANCEL
          </Button>
          {canSave && (
            <Button
              type="primary"
              disabled={pending || !configurationLoaded}
              id={`edit-configuration-form-${isNew ? 'create' : 'save'}-button`}
              onClick={handleSubmit}
            >
              {isNew ? 'CREATE' : 'SAVE'}
            </Button>
          )}
        </div>
      </div>
    );

  const allTabItems = [
    {
      key: 'info',
      label: 'Info',
      children: (
        <>
          <Form.Item
            {...FORM_ITEM_LAYOUT}
            label="Configuration name"
            name="name"
            rules={[{required: true, message: 'Configuration name is required'}]}
          >
            <Input
              disabled={pending || !writeAllowed}
              ref={(input) => {
                const el = input as unknown as HTMLInputElement | null;
                nameInputRef.current = el;
                if (el) {
                  el.onfocus = function (this: HTMLInputElement) {
                    setTimeout(() => {
                      this.selectionStart = (this.value || '').length;
                      this.selectionEnd = (this.value || '').length;
                    }, 0);
                  };
                }
              }}
              onPressEnter={handleSubmit}
            />
          </Form.Item>
          <Form.Item {...FORM_ITEM_LAYOUT} label="Configuration description" name="description">
            <Input.TextArea
              autoSize={{minRows: 2, maxRows: 6}}
              disabled={pending || !writeAllowed}
            />
          </Form.Item>
        </>
      ),
    },
    ...(configuration?.id
      ? [
          {
            key: 'permissions',
            label: 'Permissions',
            children: <ConfigurationPermissionsTab configuration={configuration} />,
          },
        ]
      : []),
  ];

  return (
    <>
      <Modal
        destroyOnHidden
        mask={{closable: !pending}}
        afterClose={handleAfterClose}
        closable={!pending}
        open={open}
        title={modalTitle}
        onCancel={onClose}
        footer={footer}
      >
        <Spin spinning={pending}>
          <Form form={form}>
            <Tabs size="small" activeKey={activeTab} onChange={setActiveTab} items={allTabItems} />
          </Form>
        </Spin>
      </Modal>
    </>
  );
}

export {ConfigurationEditModal};
