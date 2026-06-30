import {useCallback, useRef, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {Button, Dropdown, message} from 'antd';
import {CopyOutlined, EditOutlined, SettingOutlined} from '@ant-design/icons';
import {PipelineCloneModal} from '../../../shared/object-actions/pipeline/clone/pipeline-clone-modal.tsx';

import type {CommonProps} from '../../../../@types/common.ts';
import type {Folder, LibraryRootFolder, Pipeline} from '../../../../@types/library.ts';
import {
  folderKeys,
  libraryTreeKeys,
  pipelineKeys,
  pipelineQueryOptions,
  pipelinesKeys,
  queryClient,
} from '../../../../queries';
import {updatePipeline} from '../../../../api';
import localization from '../../../../utils/localization.jsx';
import EditPipelineForm from '../../../pipelines/version/forms/EditPipelineForm.jsx';
import {LegacyMobXStoresProvider} from '../../../../pages/_shared/legacy-mobx-stores-provider.tsx';
import {legacyMobXStores} from '../../../../mobx-stores/legacy-stores.js';
import UpdatePipelineToken from '../../../../models/pipelines/UpdatePipelineToken.js';
import DeletePipeline from '../../../../models/pipelines/DeletePipeline.js';

function updatePipelineInLibraryTree(
  folder: LibraryRootFolder | Folder,
  id: number,
  updated: Pipeline,
): LibraryRootFolder | Folder {
  const pipelines = folder.pipelines?.map((p) => (p.id === id ? {...p, ...updated} : p));
  const childFolders = folder.childFolders?.map(
    (f) => updatePipelineInLibraryTree(f, id, updated) as Folder,
  );
  return {
    ...folder,
    ...(pipelines !== undefined ? {pipelines} : {}),
    ...(childFolders !== undefined ? {childFolders} : {}),
  };
}

type SettingsActionProps = CommonProps & {
  pipelineId?: number | string;
  isOwner?: boolean;
  readOnly?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {pipelineId, isOwner = false, readOnly = false} = props;
  const [open, setOpen] = useState(false);
  const [editDialogVisible, setEditDialogVisible] = useState(false);
  const [cloneDialogVisible, setCloneDialogVisible] = useState(false);
  const [operationInProgress, setOperationInProgress] = useState(false);

  const numericId = pipelineId !== undefined ? Number(pipelineId) : undefined;
  const {data: pipeline} = useQuery(pipelineQueryOptions(numericId));
  const navigate = useNavigate();
  const {pathname} = useLocation();

  const updateTokenRequest = useRef(new UpdatePipelineToken());

  const openEditDialog = useCallback(() => {
    setEditDialogVisible(true);
  }, []);

  const closeEditDialog = useCallback(() => {
    setEditDialogVisible(false);
  }, []);

  const invalidateAfterEdit = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({queryKey: pipelinesKeys.all}),
      numericId !== undefined
        ? queryClient.invalidateQueries({queryKey: pipelineKeys.detail(numericId)})
        : Promise.resolve(),
      pipeline?.parentFolderId !== undefined
        ? queryClient.invalidateQueries({
            queryKey: folderKeys.detail(pipeline.parentFolderId),
          })
        : Promise.resolve(),
      queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
    ]);
  }, [numericId, pipeline?.parentFolderId]);

  const editPipeline = useCallback(
    async (values: Record<string, unknown> = {}) => {
      if (!pipeline) return;
      const {name, description, token, branch, configurationPath, visibility, codePath, docsPath} =
        values;
      const objectName = /^versioned_storage$/i.test(pipeline.pipelineType ?? '')
        ? 'versioned storage'
        : 'pipeline';
      const localizedName = localization.localization.localizedString(objectName);
      const hide = message.loading(`Updating ${localizedName} ${name}...`, 0);
      let updatedPipeline: Pipeline;
      try {
        updatedPipeline = await updatePipeline({
          id: pipeline.id,
          name: name as string,
          description: description as string | undefined,
          parentFolderId: pipeline.parentFolderId,
          branch: branch as string | undefined,
          configurationPath: configurationPath as string | undefined,
          visibility: visibility as Pipeline['visibility'],
          codePath: codePath as string | undefined,
          docsPath: docsPath as string | undefined,
        });
      } catch (error) {
        hide();
        message.error(error instanceof Error ? error.message : 'Error updating pipeline', 5);
        return;
      }
      if (token !== undefined) {
        updateTokenRequest.current = new UpdatePipelineToken();
        await updateTokenRequest.current.send({id: pipeline.id, repositoryToken: token});
        hide();
        if (updateTokenRequest.current.error) {
          message.error(updateTokenRequest.current.error, 5);
          return;
        }
      } else {
        hide();
      }
      queryClient.setQueryData(pipelineKeys.detail(pipeline.id), updatedPipeline);
      queryClient.setQueryData(
        libraryTreeKeys.all,
        (old: LibraryRootFolder | undefined) =>
          old
            ? (updatePipelineInLibraryTree(old, pipeline.id, updatedPipeline) as LibraryRootFolder)
            : old,
      );
      void invalidateAfterEdit();
      if (numericId !== undefined) {
        void legacyMobXStores.pipelines.getPipeline(numericId).fetch();
      }
      closeEditDialog();
    },
    [pipeline, numericId, closeEditDialog, invalidateAfterEdit],
  );

  const deletePipeline = useCallback(
    async (keepRepository: boolean) => {
      if (!pipeline) return;
      const objectName = /^versioned_storage$/i.test(pipeline.pipelineType ?? '')
        ? 'versioned storage'
        : 'pipeline';
      const localizedName = localization.localization.localizedString(objectName);
      const request = new DeletePipeline(pipeline.id, keepRepository);
      const hide = message.loading(`Deleting ${localizedName} ${pipeline.name}...`, 0);
      await request.fetch();
      hide();
      if (request.error) {
        message.error(request.error, 5);
        return;
      }
      closeEditDialog();
      await invalidateAfterEdit();
      if (pathname === `/${numericId}` && pipeline.parentFolderId !== undefined) {
        navigate(`/folder/${pipeline.parentFolderId}`);
      }
    },
    [pipeline, numericId, pathname, navigate, closeEditDialog, invalidateAfterEdit],
  );

  const handleSubmitEdit = useCallback(
    (values: Record<string, unknown>) => {
      setOperationInProgress(true);
      editPipeline(values).finally(() => setOperationInProgress(false));
    },
    [editPipeline],
  );

  const handleDelete = useCallback(
    (keepRepository: boolean) => {
      setOperationInProgress(true);
      deletePipeline(keepRepository).finally(() => setOperationInProgress(false));
    },
    [deletePipeline],
  );

  const onClick = useCallback(
    ({key}: {key: string}) => {
      setOpen(false);
      switch (key) {
        case 'edit':
          openEditDialog();
          break;
        case 'clone':
          setCloneDialogVisible(true);
          break;
        default:
          break;
      }
    },
    [openEditDialog],
  );

  if (readOnly) {
    return null;
  }

  const items = [
    {
      key: 'edit',
      id: 'edit-pipeline-button',
      label: (
        <span>
          <EditOutlined /> Edit
        </span>
      ),
    },
    ...(isOwner
      ? [
          {
            key: 'clone',
            id: 'clone-pipeline-button',
            label: (
              <span>
                <CopyOutlined /> Clone
              </span>
            ),
          },
        ]
      : []),
  ];

  return (
    <>
      <Dropdown
        placement="bottomRight"
        trigger={['click']}
        open={open}
        onOpenChange={setOpen}
        menu={{items, onClick, style: {width: 100}}}
      >
        <Button key="edit" id="edit-pipeline-menu-button" size="small">
          <SettingOutlined />
        </Button>
      </Dropdown>
      {isOwner && numericId !== undefined && (
        <PipelineCloneModal
          open={cloneDialogVisible}
          onClose={() => setCloneDialogVisible(false)}
          pipelineId={numericId}
        />
      )}
      <LegacyMobXStoresProvider>
        <EditPipelineForm
          onSubmit={handleSubmitEdit}
          onCancel={closeEditDialog}
          onDelete={handleDelete}
          visible={editDialogVisible}
          pending={operationInProgress}
          pipeline={pipeline}
        />
      </LegacyMobXStoresProvider>
    </>
  );
}

export {SettingsAction};
