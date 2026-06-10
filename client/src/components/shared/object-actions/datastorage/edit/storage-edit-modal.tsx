import {Button, Form, Modal, Spin, Tabs} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {PermissionsForm} from '../../../permissions-form/index.ts';
import {TransitionRules} from '../../../transition-rules/index.ts';
import {dataStorageKeys, dataStorageQueryOptions} from '../../../../../queries';
import {useInvalidateDetailQueryOnOpen} from '../../base/hooks.ts';
import type {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';
import {StorageRemoveButton} from '../delete/storage-remove-button.tsx';
import {InfoTab} from './info-tab.tsx';
import {useDataStorageEditController} from './use-data-storage-edit-controller.ts';

type StorageEditModalNewProps = {
  isNfsMount?: boolean;
  omicsStore?: boolean;
  addExistingStorageFlag?: boolean;
  policySupported?: boolean;
};

type StorageEditModalExistingProps = {
  storageId: number;
};

type StorageEditProps = Partial<StorageEditModalNewProps & StorageEditModalExistingProps>;

export type StorageEditModalProps = ActionModalBaseProps &
  StorageEditProps & {
    onDone?: () => void;
    onDeleted?: () => void;
    storageOperationsEnabled?: boolean;
  };

function StorageEditModal({
  open,
  onClose,
  disabled,
  className,
  style,
  onDone,
  onDeleted,
  storageOperationsEnabled,
  isNfsMount,
  omicsStore,
  addExistingStorageFlag,
  policySupported,
  storageId,
}: StorageEditModalProps) {
  const isNew = storageId === undefined;
  const [form] = Form.useForm();

  useInvalidateDetailQueryOnOpen(open, dataStorageKeys.detail, storageId);

  const {
    data: storage,
    isFetching: loadPending,
    isSuccess: loaded,
  } = useQuery(
    dataStorageQueryOptions(storageId, {
      enabled: !isNew && open,
    }),
  );

  const dataStorage = isNew ? undefined : storage;
  const storageLoaded = isNew || loaded;
  const resolvedPolicySupported = isNew
    ? policySupported
    : (storage?.policySupported ?? policySupported);

  const ctrl = useDataStorageEditController({
    form,
    dataStorage,
    isNfsMount: isNew ? isNfsMount : undefined,
    omicsStore: isNew ? omicsStore : undefined,
    policySupported: resolvedPolicySupported,
    storageOperationsEnabled,
    onDone,
    onClose,
  });

  const {
    isNfsMount: isNfs,
    omicsStore: isOmics,
    isReadOnly,
    restrictedAccess,
    restrictedAccessCheckInProgress,
    submitPending,
    activeTab,
    setActiveTab,
    permissionsRestrictions: {defaultMask, enabledMask, readOnlyRoles},
    transitionRulesAvailable,
    transitionRulesReadOnly,
    handleSubmit,
    handleAfterClose,
    nfsStoragePathValid,
    aliasValid,
    omicsType,
  } = ctrl;

  const pending = submitPending || restrictedAccessCheckInProgress;
  const canShowDelete = !isNew && !isReadOnly && !restrictedAccess;

  const title = dataStorage
    ? isNfs
      ? 'Edit FS mount'
      : isOmics
        ? 'Edit AWS HealthOmics Store'
        : 'Edit object storage'
    : isNfs
      ? 'Create FS mount'
      : addExistingStorageFlag
        ? 'Add existing object storage'
        : isOmics
          ? 'Create AWS HealthOmics Store'
          : 'Create object storage';

  const editFooter =
    !isReadOnly && !restrictedAccess && dataStorage ? (
      <div
        className={`cp-modal-footer-actions${canShowDelete ? ' cp-modal-footer-actions--split' : ''}`}
      >
        {canShowDelete && (
          <div className="cp-modal-footer-actions-group">
            <StorageRemoveButton storage={dataStorage.id} onRemove={onDeleted}>
              DELETE
            </StorageRemoveButton>
          </div>
        )}
        <div className="cp-modal-footer-actions-group cp-modal-footer-actions-group--end">
          <Button id="edit-storage-dialog-cancel-button" disabled={pending} onClick={onClose}>
            CANCEL
          </Button>
          <Button
            id="edit-storage-dialog-save-button"
            type="primary"
            disabled={pending}
            onClick={handleSubmit}
          >
            SAVE
          </Button>
        </div>
      </div>
    ) : (
      <div className="cp-modal-footer-actions">
        <Button id="edit-storage-dialog-cancel-button" onClick={onClose}>
          CANCEL
        </Button>
      </div>
    );

  const createFooter = (
    <div className="cp-modal-footer-actions">
      <Button id="edit-storage-dialog-cancel-button" onClick={onClose}>
        Cancel
      </Button>
      <Button
        id="edit-storage-dialog-create-button"
        type="primary"
        disabled={(isNfs && !nfsStoragePathValid) || (isOmics && (!omicsType || !aliasValid))}
        onClick={handleSubmit}
      >
        Create
      </Button>
    </div>
  );

  const modalFooter = pending
    ? false
    : activeTab !== 'info'
      ? false
      : isNew
        ? createFooter
        : editFooter;

  const tabItems = [
    {
      key: 'info',
      label: 'Info',
      children: (
        <Form form={form} initialValues={ctrl.initialValues} key={dataStorage?.id ?? 'new'}>
          <InfoTab
            ctrl={ctrl}
            isNew={isNew}
            policySupported={resolvedPolicySupported}
            addExistingStorageFlag={addExistingStorageFlag}
            visible={open}
            pending={submitPending}
          />
        </Form>
      ),
    },
    ...(dataStorage?.id
      ? [
          {
            key: 'permissions',
            label: 'Permissions',
            children: (
              <PermissionsForm
                readonly={isReadOnly}
                objectIdentifier={dataStorage.id}
                objectType="DATA_STORAGE"
                defaultMask={defaultMask}
                enabledMask={enabledMask}
                readOnlyRoles={readOnlyRoles}
              />
            ),
          },
        ]
      : []),
    ...(transitionRulesAvailable && dataStorage?.id
      ? [
          {
            key: 'transitionRules',
            label: 'Transition rules',
            children: (
              <TransitionRules storageId={dataStorage.id} readOnly={transitionRulesReadOnly} />
            ),
          },
        ]
      : []),
  ];

  if (open && !isNew && loadPending && !storageLoaded) {
    return (
      <Modal
        className={className}
        style={style}
        open
        onCancel={onClose}
        footer={null}
        closable
        title="Loading storage..."
      >
        <Spin />
      </Modal>
    );
  }

  return (
    <Modal
      className={className}
      mask={{closable: !pending}}
      afterClose={handleAfterClose}
      closable={!pending}
      open={open && storageLoaded}
      title={title}
      onCancel={onClose}
      style={{transition: 'width 0.2s ease', ...style}}
      width={600}
      footer={modalFooter}
    >
      <Spin spinning={pending}>
        <Tabs size="small" activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
      </Spin>
    </Modal>
  );
}

export {StorageEditModal};
