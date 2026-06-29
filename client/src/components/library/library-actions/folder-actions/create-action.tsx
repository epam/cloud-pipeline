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
  storagesKeys,
} from '../../../../queries';
import roleModel from '../../../../utils/roleModel.jsx';
import localization from '../../../../utils/localization.jsx';
import {extractFileShareMountList} from '../../../pipelines/browser/forms/data-storage-path-input/index.tsx';
import {RepositoryTypes} from '../../../special/git-repository-control/index.jsx';
import EditPipelineForm from '../../../pipelines/version/forms/EditPipelineForm.jsx';
import EditFolderForm from '../../../pipelines/browser/forms/EditFolderForm.jsx';
import VersionedStorageDialog from '../../../pipelines/browser/forms/VersionedStorageDialog.jsx';
import {LegacyMobXStoresProvider} from '../../../../pages/_shared/legacy-mobx-stores-provider.tsx';
import {StorageEditModal} from '../../../shared/object-actions/datastorage/edit/storage-edit-modal.tsx';
import CreatePipeline from '../../../../models/pipelines/CreatePipeline.js';
import CheckPipelineRepository from '../../../../models/pipelines/CheckPipelineRepository.js';
import PipelineFolderUpdate from '../../../../models/pipelines/PipelineFolderUpdate.js';
import FolderRegister from '../../../../models/folders/FolderRegister.js';
import {loadPipeline} from '../../../../api/pipeline/pipeline-api.ts';
import {CREATE_ACTION_KEYS} from './folder-action-keys.ts';
import EditDetachedConfigurationForm from '../../../pipelines/configuration/forms/EditDetachedConfigurationForm.jsx';
import ConfigurationUpdate from '../../../../models/configuration/ConfigurationUpdate.js';
import {useFolderManagerRoles} from './folder-action-roles.ts';
import type {FolderActionMenuItem, FolderActionMenuItems} from './folder-action-types.ts';
import {asAntdMenuItems} from './folder-action-types.ts';
import {useFolderActionTemplates} from './use-folder-action-templates.ts';

function splitFolderPaths(foldersStructure: string): string[] {
  const uniquePaths = [...new Set(foldersStructure.split('\n').map((path) => path.trim()))];
  return uniquePaths.filter(
    (filteredPath) =>
      !uniquePaths.some((path) => filteredPath !== path && path.startsWith(filteredPath)),
  );
}

async function bulkCreateVSFolders(
  foldersStructure: string,
  pipelineId: number,
  initialCommitId: string,
): Promise<void> {
  const paths = splitFolderPaths(foldersStructure);
  if (paths.length === 0) return;
  const hide = message.loading('Creating initial folders structure...', 0);
  let lastCommitId = initialCommitId;
  for (const path of paths) {
    const request = new PipelineFolderUpdate(pipelineId);
    await request.send({lastCommitId, path, comment: `Creating folder ${path}`});
    if (request.error) {
      message.error(request.error, 5);
      break;
    }
    if (request.value?.id) {
      lastCommitId = request.value.id;
    } else {
      break;
    }
  }
  hide();
}

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

  const [createStorageVisible, setCreateStorageVisible] = useState(false);
  const [createStorageNfs, setCreateStorageNfs] = useState(false);
  const [createStorageOmics, setCreateStorageOmics] = useState(false);
  const [createStorageAddExisting, setCreateStorageAddExisting] = useState(false);

  const [createVersionedStorageVisible, setCreateVersionedStorageVisible] = useState(false);
  const [versionedStoragePending, setVersionedStoragePending] = useState(false);
  const createVersionedStorageRequest = useRef(new CreatePipeline());

  const [createFolderVisible, setCreateFolderVisible] = useState(false);
  const [folderTemplate, setFolderTemplate] = useState<TemplateDescription | null>(null);
  const [folderOperationInProgress, setFolderOperationInProgress] = useState(false);

  const [createConfigurationVisible, setCreateConfigurationVisible] = useState(false);
  const [configurationPending, setConfigurationPending] = useState(false);

  const createVersionedStorage = useCallback(
    async (opts: Record<string, unknown> = {}) => {
      const {name, description, foldersStructure} = opts as {
        name?: string;
        description?: string;
        foldersStructure?: string;
      };
      const hide = message.loading(`Creating versioned storage ${name}...`, 0);
      await createVersionedStorageRequest.current.send({
        name,
        description,
        parentFolderId: folderId,
        pipelineType: 'VERSIONED_STORAGE',
      });
      hide();
      if (createVersionedStorageRequest.current.error) {
        message.error(createVersionedStorageRequest.current.error, 5);
        return;
      }
      if (foldersStructure?.length) {
        const pipeline = await loadPipeline(createVersionedStorageRequest.current.value.id);
        if (pipeline.currentVersion?.commitId) {
          await bulkCreateVSFolders(foldersStructure, pipeline.id, pipeline.currentVersion.commitId);
        }
      }
      setCreateVersionedStorageVisible(false);
      await Promise.all([
        queryClient.invalidateQueries({queryKey: pipelinesKeys.all}),
        queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)}),
        queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
      ]);
      onObjectCreated?.();
    },
    [folderId, onObjectCreated],
  );

  const handleSubmitCreateVersionedStorage = useCallback(
    (opts: Record<string, unknown>) => {
      setVersionedStoragePending(true);
      createVersionedStorage(opts).finally(() => setVersionedStoragePending(false));
    },
    [createVersionedStorage],
  );

  const createFolder = useCallback(
    async ({name}: {name: string}) => {
      const request = new FolderRegister(folderTemplate?.id);
      const hide = message.loading(
        folderTemplate ? `Creating ${folderTemplate.id.toLowerCase()} folder...` : 'Creating folder...',
        0,
      );
      await request.send({parentId: folderId, name});
      hide();
      if (request.error) {
        message.error(request.error, 5);
        return;
      }
      setCreateFolderVisible(false);
      setFolderTemplate(null);
      await Promise.all([
        queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)}),
        queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
      ]);
      onObjectCreated?.();
    },
    [folderId, folderTemplate, onObjectCreated],
  );

  const handleSubmitCreateFolder = useCallback(
    (values: {name: string}) => {
      setFolderOperationInProgress(true);
      createFolder(values).finally(() => setFolderOperationInProgress(false));
    },
    [createFolder],
  );

  const createConfiguration = useCallback(
    async ({name, description}: {name: string; description?: string}) => {
      const hide = message.loading(`Creating configuration '${name}'...`, 0);
      const request = new ConfigurationUpdate();
      await request.send({
        name,
        description,
        parentId: folderId,
        entries: [{name: 'default', default: true, configuration: {cmd_template: 'sleep infinity'}}],
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
        return;
      }
      setCreateConfigurationVisible(false);
      await Promise.all([
        queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)}),
        queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
      ]);
      onObjectCreated?.();
    },
    [folderId, onObjectCreated],
  );

  const handleSubmitCreateConfiguration = useCallback(
    (values: {name: string; description?: string}) => {
      setConfigurationPending(true);
      createConfiguration(values).finally(() => setConfigurationPending(false));
    },
    [createConfiguration],
  );

  const openCreateStorageDialog = useCallback(
    (isNfs: boolean, isOmics: boolean, addExisting: boolean) => {
      setCreateStorageNfs(isNfs);
      setCreateStorageOmics(isOmics);
      setCreateStorageAddExisting(addExisting);
      setCreateStorageVisible(true);
    },
    [],
  );

  const onStorageCreated = useCallback(async () => {
    setCreateStorageVisible(false);
    await Promise.all([
      queryClient.invalidateQueries({queryKey: storagesKeys.all}),
      queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)}),
      queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
    ]);
    onObjectCreated?.();
  }, [folderId, onObjectCreated]);

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
          const isNfs = identifier === CREATE_ACTION_KEYS.nfsStorage;
          const isOmics = identifier === CREATE_ACTION_KEYS.omicsStore;
          const addExisting = identifier === 'existing';
          openCreateStorageDialog(isNfs, isOmics, addExisting);
          break;
        }
        case CREATE_ACTION_KEYS.versionedStorage:
          setCreateVersionedStorageVisible(true);
          break;
        case CREATE_ACTION_KEYS.folder: {
          const template = identifier
            ? folderTemplates.find((item) => item.id === identifier) ?? null
            : null;
          setFolderTemplate(template);
          setCreateFolderVisible(true);
          break;
        }
        case CREATE_ACTION_KEYS.configuration:
          setCreateConfigurationVisible(true);
          break;
        default:
          break;
      }
      onObjectCreated?.();
    },
    [folderId, folderTemplates, onObjectCreated, openCreatePipelineDialog, openCreateStorageDialog, pipelineTemplates],
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
        <StorageEditModal
          open={createStorageVisible}
          onClose={() => setCreateStorageVisible(false)}
          onDone={onStorageCreated}
          isNfsMount={createStorageNfs}
          omicsStore={createStorageOmics}
          addExistingStorageFlag={createStorageAddExisting}
          policySupported={!createStorageNfs}
          parentFolderId={folderId}
        />
        <VersionedStorageDialog
          visible={createVersionedStorageVisible}
          onCancel={() => setCreateVersionedStorageVisible(false)}
          onSubmit={handleSubmitCreateVersionedStorage}
          pending={versionedStoragePending}
          folderStructureArea
        />
        <EditFolderForm
          visible={createFolderVisible}
          title={folderTemplate ? `Create ${folderTemplate.id.toLowerCase()} folder` : 'Create folder'}
          pending={folderOperationInProgress}
          onSubmit={handleSubmitCreateFolder}
          onCancel={() => {
            setCreateFolderVisible(false);
            setFolderTemplate(null);
          }}
        />
      </LegacyMobXStoresProvider>
      <EditDetachedConfigurationForm
        visible={createConfigurationVisible}
        pending={configurationPending}
        onSubmit={handleSubmitCreateConfiguration}
        onCancel={() => setCreateConfigurationVisible(false)}
      />
    </>
  );
}

export {CreateAction};
