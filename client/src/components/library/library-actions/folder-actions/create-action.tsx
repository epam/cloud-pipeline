import {useCallback, useMemo, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Button, Dropdown, Modal, Row} from 'antd';
import type {MenuProps} from 'antd';
import {message} from 'antd';
import {
  DownOutlined,
  FolderOutlined,
  ForkOutlined,
  HddOutlined,
  PlusOutlined,
  SettingOutlined,
} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import type {TemplateDescription} from '../../../../@types/app.ts';
import {
  cloudRegionsQueryOptions,
  folderKeys,
  folderQueryOptions,
  libraryTreeKeys,
  pipelinesKeys,
  queryClient,
} from '../../../../queries';
import roleModel from '../../../../utils/roleModel.jsx';
import localization from '../../../../utils/localization.jsx';
import {extractFileShareMountList} from '../../../pipelines/browser/forms/data-storage-path-input/index.tsx';
import {RepositoryTypes} from '../../../special/git-repository-control/index.jsx';
import EditPipelineForm from '../../../pipelines/version/forms/EditPipelineForm.jsx';
import {LegacyMobXStoresProvider} from '../../../../pages/_shared/legacy-mobx-stores-provider.tsx';
import CreatePipeline from '../../../../models/pipelines/CreatePipeline.js';
import CheckPipelineRepository from '../../../../models/pipelines/CheckPipelineRepository.js';
import {CREATE_ACTION_KEYS} from './folder-action-keys.ts';
import {
  mockOpenAddFolderDialog,
  mockOpenCreateConfigurationDialog,
  mockOpenCreateStorageDialog,
  mockOpenCreateVersionedStorageDialog,
} from './folder-action-mocks.ts';
import {useFolderManagerRoles} from './folder-action-roles.ts';
import type {FolderActionMenuItem, FolderActionMenuItems} from './folder-action-types.ts';
import {asAntdMenuItems} from './folder-action-types.ts';
import {useFolderActionTemplates} from './use-folder-action-templates.ts';

type CreateActionProps = CommonProps & {
  folderId: number;
  readOnly?: boolean;
  onObjectCreated?: () => void;
};

function parseCreateActionKey(key: string): {
  type: string;
  identifier?: string;
} {
  const parts = key.split('_');
  const type = parts[0];
  let identifier: string | undefined;
  if (parts.length > 1) {
    parts.splice(0, 1);
    identifier = parts.join('_');
  }
  return {type, identifier};
}

function CreateAction(props: CreateActionProps) {
  const {folderId, readOnly = false, onObjectCreated} = props;
  const [open, setOpen] = useState(false);
  const {data: folder} = useQuery(folderQueryOptions(folderId));
  const roles = useFolderManagerRoles();

  const [createPipelineDialogVisible, setCreatePipelineDialogVisible] = useState(false);
  const [pipelineTemplate, setPipelineTemplate] = useState<TemplateDescription | null>(null);
  const [pipelineOperationInProgress, setPipelineOperationInProgress] = useState(false);

  const createPipelineRequest = useRef(new CreatePipeline());
  const checkRequest = useRef(new CheckPipelineRepository());

  const openCreatePipelineDialog = useCallback((template: TemplateDescription | null) => {
    setPipelineTemplate(template ?? null);
    setCreatePipelineDialogVisible(true);
  }, []);

  const closeCreatePipelineDialog = useCallback(() => {
    setCreatePipelineDialogVisible(false);
    setPipelineTemplate(null);
  }, []);

  const createPipeline = useCallback(
    async (opts: Record<string, unknown> = {}) => {
      const {
        name,
        description,
        repository,
        repositoryType,
        branch,
        token,
        configurationPath,
        visibility,
        codePath,
        docsPath,
      } = opts;
      const localizedPipeline = localization.localization.localizedString('pipeline');
      const hide = message.loading(`Creating ${localizedPipeline} ${name}...`, 0);
      await createPipelineRequest.current.send({
        name,
        description,
        parentFolderId: folderId,
        templateId: pipelineTemplate?.id,
        repository,
        repositoryType,
        repositoryToken: token,
        branch,
        configurationPath,
        visibility,
        codePath,
        docsPath,
      });
      hide();
      if (createPipelineRequest.current.error) {
        message.error(createPipelineRequest.current.error, 5);
      } else {
        closeCreatePipelineDialog();
        await queryClient.invalidateQueries({queryKey: pipelinesKeys.all});
        await queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)});
        await queryClient.invalidateQueries({queryKey: libraryTreeKeys.all});
        onObjectCreated?.();
      }
    },
    [folderId, pipelineTemplate, closeCreatePipelineDialog, onObjectCreated],
  );

  const checkRepositoryExistenceAndCreate = useCallback(
    async (pipelineOpts: Record<string, unknown>) => {
      const {repository, repositoryType, branch, token} = pipelineOpts as {
        repository?: string;
        repositoryType?: string;
        branch?: string;
        token?: string;
      };
      if ((token && token.length) || (repository && repository.length)) {
        const hide = message.loading('Checking repository existence...', -1);
        await checkRequest.current.send({repository, type: repositoryType, branch, token});
        hide();
        if (checkRequest.current.error) {
          return message.error(checkRequest.current.error);
        }
        if (!checkRequest.current.value.repositoryExists) {
          if (repositoryType === RepositoryTypes.GitLab) {
            return Modal.confirm({
              title: 'Repository does not exist. Create?',
              style: {wordWrap: 'break-word'},
              content: null,
              okText: 'OK',
              cancelText: 'Cancel',
              onOk: async () => {
                await createPipeline(pipelineOpts);
              },
            });
          }
          return message.error(`Repository ${repository} does not exist.`);
        }
      }
      return createPipeline(pipelineOpts);
    },
    [createPipeline],
  );

  const handleSubmitCreatePipeline = useCallback(
    (opts: Record<string, unknown>) => {
      setPipelineOperationInProgress(true);
      checkRepositoryExistenceAndCreate(opts).finally(() => {
        setPipelineOperationInProgress(false);
      });
    },
    [checkRepositoryExistenceAndCreate],
  );
  const {
    pipelineTemplates,
    folderTemplates,
    pending: templatesPending,
    error: templatesError,
  } = useFolderActionTemplates();
  const {data: cloudRegions = [], isSuccess: cloudRegionsLoaded} = useQuery(
    cloudRegionsQueryOptions(),
  );

  const isAnyAwsRegion = useMemo(
    () => cloudRegions.some((region) => region.provider === 'AWS'),
    [cloudRegions],
  );

  const fsMountsAvailable = useMemo(
    () => cloudRegionsLoaded && extractFileShareMountList(cloudRegions).length > 0,
    [cloudRegions, cloudRegionsLoaded],
  );

  const onCreateActionSelect = useCallback<NonNullable<MenuProps['onClick']>>(
    ({key}) => {
      setOpen(false);
      const {type, identifier} = parseCreateActionKey(key);
      switch (type) {
        case CREATE_ACTION_KEYS.pipeline: {
          const template =
            identifier && identifier !== 'default'
              ? pipelineTemplates.find((item) => item.id === identifier)
              : null;
          openCreatePipelineDialog(template ?? null);
          break;
        }
        case CREATE_ACTION_KEYS.storage: {
          const createNfs = identifier === CREATE_ACTION_KEYS.nfsStorage;
          const createOmics = identifier === CREATE_ACTION_KEYS.omicsStore;
          const createNew = createNfs
            ? true
            : createOmics
              ? true
              : identifier
                ? identifier === 'new'
                : true;
          mockOpenCreateStorageDialog(folderId, createNew, createNfs, createOmics);
          break;
        }
        case CREATE_ACTION_KEYS.versionedStorage:
          mockOpenCreateVersionedStorageDialog(folderId);
          break;
        case CREATE_ACTION_KEYS.folder: {
          const template = identifier
            ? folderTemplates.find((item) => item.id === identifier)
            : null;
          mockOpenAddFolderDialog(folderId, template);
          break;
        }
        case CREATE_ACTION_KEYS.configuration:
          mockOpenCreateConfigurationDialog(folderId);
          break;
        default:
          break;
      }
      onObjectCreated?.();
    },
    [folderId, folderTemplates, onObjectCreated, openCreatePipelineDialog, pipelineTemplates],
  );

  const menuItems = useMemo(() => {
    if (!folder) {
      return [];
    }

    // Folder.jsx: roleModel.writeAllowed(folder) && !readOnly && folderId !== undefined && !listingMode
    if (readOnly || !roleModel.writeAllowed(folder)) {
      return [];
    }

    const items: FolderActionMenuItems = [];

    // Folder.jsx: roleModel.isManager.pipeline || roleModel.isManager.pipelineAdmin
    if (roles.isPipelineManager || roles.isPipelineAdmin) {
      if (!templatesPending && !templatesError && pipelineTemplates.length > 0) {
        const templatesList = pipelineTemplates.filter((template) => !template.defaultTemplate);
        const pipelineTemplateItems: FolderActionMenuItems = [
          {
            key: `${CREATE_ACTION_KEYS.pipeline}_default`,
            label: (
              <>
                <Row>DEFAULT</Row>
                <Row style={{fontSize: 'smaller'}}>Create pipeline without template</Row>
              </>
            ),
            id: 'create-pipeline-button',
            className: 'create-pipeline-button',
          },
          {type: 'divider', key: 'pipeline-divider'},
          ...templatesList.map((template) => ({
            key: `${CREATE_ACTION_KEYS.pipeline}_${template.id}`,
            label: (
              <>
                <Row>{template.id.toUpperCase()}</Row>
                <Row style={{fontSize: 'smaller'}}>{template.description}</Row>
              </>
            ),
            id: `create-pipeline-by-template-button-${template.id.toLowerCase()}`,
            className: `create-pipeline-by-template-button-${template.id.toLowerCase()}`,
          })),
        ];
        items.push({
          key: CREATE_ACTION_KEYS.pipeline,
          label: (
            <span>
              <ForkOutlined /> Pipeline
            </span>
          ),
          id: 'create-pipeline-sub-menu-button',
          className: 'create-pipeline-sub-menu-button',
          children: pipelineTemplateItems,
        });
      } else {
        items.push({
          key: CREATE_ACTION_KEYS.pipeline,
          label: (
            <>
              <ForkOutlined /> Pipeline
            </>
          ),
          id: 'create-pipeline-button',
          className: 'create-pipeline-button',
        });
      }
    }

    // Folder.jsx: roleModel.isManager.storage || (roleModel.isManager.storageAdmin && roleModel.writeAllowed(folder))
    if (roles.isStorageManager || (roles.isStorageAdmin && roleModel.writeAllowed(folder))) {
      items.push({
        key: CREATE_ACTION_KEYS.storage,
        label: (
          <span>
            <HddOutlined /> Storages
          </span>
        ),
        className: 'create-storage-sub-menu',
        children: [
          {
            key: `${CREATE_ACTION_KEYS.storage}_new`,
            label: 'Create new object storage',
            id: 'create-new-storage-button',
            className: 'create-new-storage-button',
          },
          ...(isAnyAwsRegion
            ? [
                {
                  key: `${CREATE_ACTION_KEYS.storage}_${CREATE_ACTION_KEYS.omicsStore}`,
                  label: 'Create AWS HealthOmics Store',
                  id: 'create-omics-store-button',
                  className: 'create-omics-store-button',
                } satisfies FolderActionMenuItem,
              ]
            : []),
          {
            key: `${CREATE_ACTION_KEYS.storage}_existing`,
            label: 'Add existing object storage',
            id: 'add-existing-storage-button',
            className: 'add-existing-storage-button',
          },
          ...(fsMountsAvailable
            ? ([
                {type: 'divider', key: 'storages_divider'},
                {
                  key: `${CREATE_ACTION_KEYS.storage}_${CREATE_ACTION_KEYS.nfsStorage}`,
                  label: 'Create new FS mount',
                  id: 'create-new-nfs-mount',
                  className: 'create-new-nfs-mount',
                },
              ] satisfies FolderActionMenuItem[])
            : []),
        ] satisfies FolderActionMenuItem[],
      });
    }

    // Folder.jsx: roleModel.isManager.configuration
    if (roles.isConfigurationManager) {
      items.push({
        key: CREATE_ACTION_KEYS.configuration,
        label: (
          <>
            <SettingOutlined /> Configuration
          </>
        ),
        id: 'create-configuration-button',
        className: 'create-configuration-button',
      });
    }

    let folderTemplateMenuItems: FolderActionMenuItems = [];
    // Folder.jsx: roleModel.isManager.folder && folderTemplates loaded
    if (
      roles.isFolderManager &&
      !templatesPending &&
      !templatesError &&
      folderTemplates.length > 0
    ) {
      folderTemplateMenuItems = folderTemplates.map((template) => ({
        key: `${CREATE_ACTION_KEYS.folder}_${template.id}`,
        label: (
          <>
            <Row>{template.id.toUpperCase()}</Row>
            <Row style={{fontSize: 'smaller'}}>{template.description}</Row>
          </>
        ),
        id: `create-folder-by-template-button-${template.id.toLowerCase()}`,
        className: `create-folder-by-template-button-${template.id.toLowerCase()}`,
      }));
    }

    // Folder.jsx: roleModel.isManager.folder
    if (roles.isFolderManager) {
      items.push({
        key: CREATE_ACTION_KEYS.folder,
        label: (
          <>
            <FolderOutlined /> Folder
          </>
        ),
        id: 'create-folder-button',
        className: 'create-folder-button',
      });
      if (folderTemplateMenuItems.length > 0) {
        items.push({type: 'divider', key: 'divider one'});
        items.push(...folderTemplateMenuItems);
      }
    }

    // Folder.jsx: roleModel.isManager.versionedStorage
    if (roles.isVersionedStorageManager) {
      if (folderTemplateMenuItems.length === 0) {
        items.push({type: 'divider', key: 'divider versioned storages'});
      }
      items.push({
        key: CREATE_ACTION_KEYS.versionedStorage,
        label: (
          <>
            <Row style={{textTransform: 'uppercase'}}>versioned storage</Row>
            <Row style={{fontSize: 'smaller'}}>storage with revision control</Row>
          </>
        ),
        id: 'create-versioned-storage-button',
        className: 'create-versioned-storage-button',
      });
    }

    return items;
  }, [
    folder,
    readOnly,
    roles,
    templatesPending,
    templatesError,
    pipelineTemplates,
    folderTemplates,
    isAnyAwsRegion,
    fsMountsAvailable,
  ]);

  if (menuItems.length === 0) {
    return null;
  }

  return (
    <>
      <Dropdown
        trigger={['click']}
        open={open}
        onOpenChange={setOpen}
        placement="bottomRight"
        menu={{
          items: asAntdMenuItems(menuItems),
          onClick: onCreateActionSelect,
          mode: 'vertical',
          subMenuOpenDelay: 0.2,
          subMenuCloseDelay: 0.2,
        }}
      >
        <Button type="primary" size="small" id="create-button">
          <PlusOutlined />
          <span>Create</span>
          <DownOutlined />
        </Button>
      </Dropdown>
      <LegacyMobXStoresProvider>
        <EditPipelineForm
          onSubmit={handleSubmitCreatePipeline}
          onCancel={closeCreatePipelineDialog}
          visible={createPipelineDialogVisible}
          pipelineTemplate={pipelineTemplate}
          pending={pipelineOperationInProgress}
        />
      </LegacyMobXStoresProvider>
    </>
  );
}

export {CreateAction};
