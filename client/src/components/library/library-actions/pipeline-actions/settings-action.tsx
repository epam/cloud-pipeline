import {useCallback, useRef, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {Button, Dropdown, message} from 'antd';
import {CopyOutlined, EditOutlined, SettingOutlined} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import {
  folderKeys,
  libraryTreeKeys,
  pipelineKeys,
  pipelineQueryOptions,
  pipelinesKeys,
  queryClient,
} from '../../../../queries';
import localization from '../../../../utils/localization.jsx';
import EditPipelineForm from '../../../pipelines/version/forms/EditPipelineForm.jsx';
import {LegacyMobXStoresProvider} from '../../../../pages/_shared/legacy-mobx-stores-provider.tsx';
import UpdatePipeline from '../../../../models/pipelines/UpdatePipeline.js';
import UpdatePipelineToken from '../../../../models/pipelines/UpdatePipelineToken.js';
import DeletePipeline from '../../../../models/pipelines/DeletePipeline.js';

type SettingsActionProps = CommonProps & {
  pipelineId?: number | string;
  isOwner?: boolean;
  readOnly?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {pipelineId, isOwner = false, readOnly = false} = props;
  const [open, setOpen] = useState(false);
  const [editDialogVisible, setEditDialogVisible] = useState(false);
  const [operationInProgress, setOperationInProgress] = useState(false);

  const numericId = pipelineId !== undefined ? Number(pipelineId) : undefined;
  const {data: pipeline} = useQuery(pipelineQueryOptions(numericId));
  const navigate = useNavigate();
  const {pathname} = useLocation();

  const updateRequest = useRef(new UpdatePipeline());
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
      await updateRequest.current.send({
        id: pipeline.id,
        name,
        description,
        parentFolderId: pipeline.parentFolderId,
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
      closeEditDialog();
      await invalidateAfterEdit();
    },
    [pipeline, closeEditDialog, invalidateAfterEdit],
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
          message.info(`[mock] Clone pipeline ${pipelineId}`);
          break;
        default:
          break;
      }
    },
    [pipelineId, openEditDialog],
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

  if (items.length === 0) {
    return null;
  }

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
