import {Button, Form, message, Spin, Tabs} from 'antd';
import Modal from 'antd/es/modal/Modal';
import {useCallback, useEffect, useState, MouseEvent, KeyboardEvent} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';
import {
  folderKeys,
  folderQueryOptions,
  libraryTreeKeys,
  pipelineKeys,
  pipelineQueryOptions,
} from '../../../../../queries';
import {registerPipeline, updatePipeline} from '../../../../../api/pipeline/pipeline-api.ts';
import {Pipeline, PipelineType} from '../../../../../@types/library.ts';
import {PipelineVO} from '../../../../../@types/pipeline.ts';
import {RepositoryTypes} from '../../../../special/git-repository-control';
import roleModel from '../../../../../utils/roleModel';
import {preventDefaultAndStopPropagation} from '../../../../../utilities/callbacks.ts';
import DeletePipelineModal from '../remove/delete-pipeline-modal.tsx';
import {PipelineInfoTab} from './pipeline-info-tab.tsx';
import {PipelinePermissionsTab} from './pipeline-permissions-tab.tsx';
import {PipelineFormValues, getInitialValues} from './pipeline-edit-form-utils.ts';
import {useInvalidateDetailQueryOnOpen} from '../../base/hooks.ts';
import {getErrorDescription} from '../../../../../utilities/errors.ts';

type PipelineEditModalNewPipelineProps = {
  parentFolderId: number | undefined;
  pipelineTemplateId?: string;
  pipelineType?: PipelineType;
};

type PipelineEditModalExistingPipelineProps = {
  pipelineId: number;
};

type PipelineEditProps = Partial<
  PipelineEditModalNewPipelineProps & PipelineEditModalExistingPipelineProps
>;

export type PipelineEditModalProps = ActionModalBaseProps & PipelineEditProps;

function isEditExistingPipelineProps(
  props: PipelineEditProps,
): props is PipelineEditModalExistingPipelineProps {
  return 'pipelineId' in props && typeof props['pipelineId'] === 'number';
}

function isNewPipelineProps(props: PipelineEditProps): props is PipelineEditModalNewPipelineProps {
  return !isEditExistingPipelineProps(props);
}

function resolvePipelineEditProps(props: PipelineEditModalProps): {
  parentFolderId: number | undefined;
  pipelineTemplateId: string | undefined;
  pipelineType: Pipeline['pipelineType'] | undefined;
  pipelineId: number | undefined;
  isNew: boolean;
} {
  if (isNewPipelineProps(props)) {
    return {
      parentFolderId: props.parentFolderId,
      pipelineTemplateId: props.pipelineTemplateId,
      pipelineType: props.pipelineType,
      pipelineId: undefined,
      isNew: true,
    };
  }
  return {
    parentFolderId: undefined,
    pipelineTemplateId: undefined,
    pipelineType: undefined,
    pipelineId: props.pipelineId,
    isNew: false,
  };
}

function PipelineEditModal(props: PipelineEditModalProps) {
  const {className, style, open, onClose, disabled} = props;
  const {
    pipelineId,
    pipelineTemplateId,
    pipelineType,
    parentFolderId: _parentFolderId,
    isNew,
  } = resolvePipelineEditProps(props);

  const queryClient = useQueryClient();
  useInvalidateDetailQueryOnOpen(open, pipelineKeys.detail, pipelineId);
  const {
    data: pipeline,
    isFetching: loadPending,
    isSuccess: loaded,
  } = useQuery({
    ...pipelineQueryOptions(pipelineId, {
      enabled: !isNew && open,
    }),
  });
  const pipelineLoaded = isNew || loaded;
  const parentFolderId = isNew ? _parentFolderId : (pipeline?.parentFolderId ?? undefined);
  const resolvedPipelineType = (isNew ? pipelineType : pipeline?.pipelineType) ?? 'PIPELINE';

  const isVersionedStorage = /^versioned_storage$/i.test(resolvedPipelineType);
  const objectLabel = isVersionedStorage ? 'versioned storage' : 'pipeline';

  const [form] = Form.useForm<PipelineFormValues>();
  const [activeTab, setActiveTab] = useState('info');
  const [showRepoSettings, setShowRepoSettings] = useState(false);
  const [githubType, setGithubType] = useState<string>(RepositoryTypes.GitHubApp);
  const [submitPending, setSubmitPending] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  useEffect(() => {
    if (open) {
      const resolvedGithubType =
        pipeline?.repositoryType === RepositoryTypes.GitHub
          ? RepositoryTypes.GitHub
          : RepositoryTypes.GitHubApp;
      setGithubType(resolvedGithubType);
      setActiveTab('info');
      setShowRepoSettings(false);
    }
  }, [open, pipeline]);

  useEffect(() => {
    if (open) {
      form.setFieldsValue(getInitialValues(pipeline));
    }
  }, [open, pipeline, form]);

  const values = Form.useWatch([], form);
  const [submittable, setSubmittable] = useState(false);
  useEffect(() => {
    form
      .validateFields({validateOnly: true})
      .then(() => setSubmittable(true))
      .catch(() => setSubmittable(false));
  }, [form, values]);

  const onCloseWrapper = useCallback(
    (event: MouseEvent | KeyboardEvent) => {
      preventDefaultAndStopPropagation(event);
      form.resetFields();
      onClose?.(event);
    },
    [onClose, form],
  );

  const onSubmit = useCallback(
    async (event: MouseEvent | KeyboardEvent) => {
      preventDefaultAndStopPropagation(event);
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
        const payload: PipelineVO = {
          name: raw.name,
          description: raw.description,
          visibility: raw.visibility,
          repositoryType: raw.repositoryType as PipelineVO['repositoryType'],
          branch: raw.branch,
          configurationPath: raw.configurationPath,
          codePath: raw.codePath,
          docsPath: raw.docsPath,
          parentFolderId,
        };

        const repoType = raw.repositoryType;
        if (repoType === RepositoryTypes.GitHub) {
          payload.repositoryType = githubType as PipelineVO['repositoryType'];
          if (githubType === RepositoryTypes.GitHub) {
            payload.repository = raw.repository;
            payload.repositoryToken = raw.token;
            payload.branch = raw.branch;
          } else {
            if (raw.githubRepository) payload.repository = raw.githubRepository;
            if (raw.githubBranch) payload.branch = raw.githubBranch;
          }
        } else {
          payload.repository = raw.repository;
          if (repoType !== RepositoryTypes.GitHubApp) {
            payload.repositoryToken = raw.token;
          }
        }

        if (isNew) {
          if (pipelineTemplateId) payload.templateId = pipelineTemplateId;
          payload.pipelineType = resolvedPipelineType;
          await registerPipeline(payload);
        } else {
          payload.id = pipelineId;
          await updatePipeline(payload);
          await queryClient.invalidateQueries({
            queryKey: pipelineKeys.detail(pipelineId as number),
          });
        }

        const folderId = isNew ? parentFolderId : (pipeline?.parentFolderId ?? parentFolderId);
        await Promise.all([
          queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
          folderId
            ? queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)})
            : Promise.resolve(),
        ]);
        onClose?.(event);
      } catch (error) {
        // keep modal open on validation or API error
        message.error(
          <span>
            Error {isNew ? 'creating' : 'updating'} pipeline: {getErrorDescription(error)}
          </span>,
          5,
        );
      } finally {
        hide();
        setSubmitPending(false);
      }
    },
    [
      form,
      isNew,
      pipelineId,
      parentFolderId,
      pipelineTemplateId,
      resolvedPipelineType,
      githubType,
      pipeline,
      queryClient,
      onClose,
    ],
  );

  const onDeleteDone = useCallback(
    (event: MouseEvent | KeyboardEvent) => {
      setDeleteOpen(false);
      onClose?.(event);
    },
    [onClose],
  );

  const isManager = isVersionedStorage
    ? roleModel.isManager.versionedStorage({props: {}})
    : roleModel.isManager.pipeline({props: {}}) || roleModel.isManager.pipelineAdmin({props: {}});

  const canWrite = isNew ? isManager : roleModel.writeAllowed(pipeline);
  const canDelete = !isNew && !!pipeline && roleModel.writeAllowed(pipeline) && isManager;

  const pending = loadPending || submitPending;

  const modalTitle = isNew
    ? pipelineTemplateId
      ? `Create ${objectLabel} (${pipelineTemplateId})`
      : `Create ${objectLabel}`
    : `Edit ${objectLabel} info`;

  const footer =
    activeTab !== 'info' ? (
      false
    ) : (
      <div className="flex items-center justify-between w-full">
        <div>
          {canDelete && (
            <Button
              danger
              disabled={pending || disabled}
              id="edit-pipeline-form-delete-button"
              onClick={() => setDeleteOpen(true)}
            >
              DELETE
            </Button>
          )}
        </div>
        <div className="flex items-center gap-1">
          <Button
            disabled={pending || disabled}
            onClick={onCloseWrapper}
            id="edit-pipeline-form-cancel-button"
          >
            CANCEL
          </Button>
          {canWrite && (
            <Button
              type="primary"
              disabled={pending || disabled || !submittable || !pipelineLoaded}
              onClick={onSubmit}
              id={`edit-pipeline-form-${isNew ? 'create' : 'save'}-button`}
            >
              {isNew ? 'CREATE' : 'SAVE'}
            </Button>
          )}
        </div>
      </div>
    );

  const tabItems = [
    {
      key: 'info',
      label: 'Info',
      children: (
        <PipelineInfoTab
          form={form}
          pipeline={pipeline}
          pipelineId={pipelineId}
          isVersionedStorage={isVersionedStorage}
          githubType={githubType}
          onGithubTypeChange={setGithubType}
          pending={pending}
          readOnly={!isNew && !canWrite}
          showRepoSettings={showRepoSettings}
          onShowRepoSettings={() => setShowRepoSettings(true)}
        />
      ),
    },
    ...(!isNew && pipeline?.id && roleModel.readAllowed(pipeline)
      ? [
          {
            key: 'permissions',
            label: 'Permissions',
            children: <PipelinePermissionsTab pipeline={pipeline} />,
          },
        ]
      : []),
  ];

  return (
    <>
      <Modal
        destroyOnHidden
        className={className}
        style={style}
        open={open}
        onCancel={onCloseWrapper}
        title={modalTitle}
        footer={pending ? false : footer}
        closable={!pending}
      >
        <Spin spinning={pending}>
          <Tabs size="small" activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
        </Spin>
      </Modal>
      {!isNew && pipelineId !== undefined && (
        <DeletePipelineModal
          open={deleteOpen}
          pipelineId={pipelineId}
          isVersionedStorage={isVersionedStorage}
          onClose={(e) => {
            preventDefaultAndStopPropagation(e);
            setDeleteOpen(false);
          }}
          onDone={onDeleteDone}
        />
      )}
    </>
  );
}

export {PipelineEditModal};
