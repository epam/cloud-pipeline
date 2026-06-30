import {useCallback, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {Button, message} from 'antd';
import {SettingOutlined} from '@ant-design/icons';

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
import EditPipelineForm from '../../../pipelines/version/forms/EditPipelineForm.jsx';
import {LegacyMobXStoresProvider} from '../../../../pages/_shared/legacy-mobx-stores-provider.tsx';
import {legacyMobXStores} from '../../../../mobx-stores/legacy-stores.js';
import DeletePipeline from '../../../../models/pipelines/DeletePipeline.js';

type SettingsActionProps = CommonProps & {
  pipelineId?: number | string;
  version?: string;
};

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

function SettingsAction({pipelineId}: SettingsActionProps) {
  const numericId = pipelineId !== undefined ? Number(pipelineId) : undefined;
  const navigate = useNavigate();

  const {data: pipeline} = useQuery(pipelineQueryOptions(numericId));

  const [editDialogVisible, setEditDialogVisible] = useState(false);
  const [operationInProgress, setOperationInProgress] = useState(false);

  const invalidateAfterEdit = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({queryKey: pipelinesKeys.all}),
      numericId !== undefined
        ? queryClient.invalidateQueries({queryKey: pipelineKeys.detail(numericId)})
        : Promise.resolve(),
      pipeline?.parentFolderId !== undefined
        ? queryClient.invalidateQueries({queryKey: folderKeys.detail(pipeline.parentFolderId)})
        : Promise.resolve(),
      queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
    ]);
  }, [numericId, pipeline?.parentFolderId]);

  const editPipeline = useCallback(
    async (values: Record<string, unknown> = {}) => {
      if (!pipeline) return;
      const {name, description, branch, configurationPath, visibility, codePath, docsPath} = values;
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
        message.error(error instanceof Error ? error.message : 'Error updating pipeline', 5);
        return;
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
      setEditDialogVisible(false);
    },
    [pipeline, numericId, invalidateAfterEdit],
  );

  const deletePipeline = useCallback(
    async (keepRepository: boolean) => {
      if (!pipeline) return;
      const request = new DeletePipeline(pipeline.id, keepRepository);
      await request.fetch();
      if (request.error) {
        message.error(request.error, 5);
        return;
      }
      setEditDialogVisible(false);
      await invalidateAfterEdit();
      if (pipeline.parentFolderId !== undefined) {
        navigate(`/folder/${pipeline.parentFolderId}`);
      } else {
        navigate('/library');
      }
    },
    [pipeline, navigate, invalidateAfterEdit],
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

  return (
    <>
      <Button
        id="edit-pipeline-button"
        size="small"
        style={{lineHeight: 1}}
        onClick={() => setEditDialogVisible(true)}
      >
        <SettingOutlined />
      </Button>
      <LegacyMobXStoresProvider>
        <EditPipelineForm
          onSubmit={handleSubmitEdit}
          onCancel={() => setEditDialogVisible(false)}
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
