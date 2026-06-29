import React, {useCallback, useRef, useState} from 'react';
import {Button, message} from 'antd';
import {SettingOutlined} from '@ant-design/icons';
import {useQuery} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';

import type {CommonProps} from '../../../../@types/common.ts';
import {
  folderKeys,
  libraryTreeKeys,
  pipelineKeys,
  pipelineQueryOptions,
  pipelinesKeys,
  queryClient,
} from '../../../../queries';
import {useInvalidateDetailQueryOnOpen} from '../../../shared/object-actions/base/hooks.ts';
import EditPipelineForm from '../../../pipelines/version/forms/EditPipelineForm.jsx';
import {LegacyMobXStoresProvider} from '../../../../pages/_shared/legacy-mobx-stores-provider.tsx';
import UpdatePipeline from '../../../../models/pipelines/UpdatePipeline.js';
import UpdatePipelineToken from '../../../../models/pipelines/UpdatePipelineToken.js';
import DeletePipeline from '../../../../models/pipelines/DeletePipeline.js';

type SettingsActionProps = CommonProps & {
  storageId?: number | string;
  readOnly?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {storageId, readOnly = false} = props;
  const [editVisible, setEditVisible] = useState(false);
  const [pending, setPending] = useState(false);

  const numericId = storageId !== undefined ? Number(storageId) : undefined;
  const navigate = useNavigate();

  useInvalidateDetailQueryOnOpen(
    editVisible && numericId !== undefined,
    pipelineKeys.detail,
    numericId,
  );

  const {data: pipeline, isFetching} = useQuery(
    pipelineQueryOptions(numericId, {enabled: editVisible && numericId !== undefined}),
  );

  // Capture parentFolderId before deletion so it survives cache invalidation
  const parentFolderIdRef = useRef<number | undefined>(undefined);
  if (pipeline?.parentFolderId !== undefined) {
    parentFolderIdRef.current = pipeline.parentFolderId;
  }

  const updateRequest = useRef(new UpdatePipeline());
  const updateTokenRequest = useRef(new UpdatePipelineToken());

  const openModal = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    e.nativeEvent.stopImmediatePropagation();
    setEditVisible(true);
  }, []);

  const handleSubmit = useCallback(
    async (values: Record<string, unknown>) => {
      if (!numericId) return;
      const {name, description, token, branch, configurationPath, visibility, codePath, docsPath} =
        values as {
          name?: string;
          description?: string;
          token?: string;
          branch?: string;
          configurationPath?: string;
          visibility?: string;
          codePath?: string;
          docsPath?: string;
        };
      const hide = message.loading(`Updating versioned storage ${name}...`, 0);
      await updateRequest.current.send({
        id: numericId,
        name,
        description,
        parentFolderId: pipeline?.parentFolderId,
        branch,
        configurationPath,
        visibility,
        codePath,
        docsPath,
      });
      if (updateRequest.current.error) {
        hide();
        message.error(updateRequest.current.error, 5);
        return;
      }
      if (token !== undefined) {
        await updateTokenRequest.current.send({id: numericId, repositoryToken: token});
        hide();
        if (updateTokenRequest.current.error) {
          message.error(updateTokenRequest.current.error, 5);
          return;
        }
      } else {
        hide();
      }
      setEditVisible(false);
      await Promise.all([
        queryClient.invalidateQueries({queryKey: pipelinesKeys.all}),
        queryClient.invalidateQueries({queryKey: pipelineKeys.detail(numericId)}),
        queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
      ]);
    },
    [numericId, pipeline?.parentFolderId],
  );

  const handleDelete = useCallback(
    async (keepRepository: boolean) => {
      if (!numericId) return;
      const hide = message.loading('Deleting versioned storage...', 0);
      const request = new DeletePipeline(numericId, keepRepository);
      await request.fetch();
      hide();
      if (request.error) {
        message.error(request.error, 5);
        return;
      }
      setEditVisible(false);
      const parentId = parentFolderIdRef.current;
      await Promise.all([
        queryClient.invalidateQueries({queryKey: pipelinesKeys.all}),
        ...(parentId !== undefined
          ? [queryClient.invalidateQueries({queryKey: folderKeys.detail(parentId)})]
          : []),
        queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
      ]);
      if (parentId !== undefined) {
        navigate(`/folder/${parentId}`);
      } else {
        navigate('/library');
      }
    },
    [numericId, navigate],
  );

  const handleSubmitWrapper = useCallback(
    (values: Record<string, unknown>) => {
      setPending(true);
      handleSubmit(values).finally(() => setPending(false));
    },
    [handleSubmit],
  );

  const handleDeleteWrapper = useCallback(
    (keepRepository: boolean) => {
      setPending(true);
      handleDelete(keepRepository).finally(() => setPending(false));
    },
    [handleDelete],
  );

  return (
    <>
      <Button size="small" disabled={readOnly} onClick={openModal}>
        <SettingOutlined />
      </Button>
      {numericId !== undefined && (
        <LegacyMobXStoresProvider>
          <EditPipelineForm
            visible={editVisible}
            loading={isFetching}
            pipeline={pipeline}
            pending={pending}
            onSubmit={handleSubmitWrapper}
            onCancel={() => setEditVisible(false)}
            onDelete={handleDeleteWrapper}
          />
        </LegacyMobXStoresProvider>
      )}
    </>
  );
}

export {SettingsAction};
