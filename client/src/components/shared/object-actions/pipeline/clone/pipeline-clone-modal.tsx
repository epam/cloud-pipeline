import {useCallback, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {Button, message, Modal} from 'antd';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import type {FormInstance} from 'antd';

import {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';
import {folderKeys, libraryTreeKeys, pipelineQueryOptions, pipelinesKeys} from '../../../../../queries';
import {LegacyMobXStoresProvider} from '../../../../../pages/_shared/legacy-mobx-stores-provider.tsx';
import roleModel from '../../../../../utils/roleModel';
import pipelinesLibrary from '../../../../../models/folders/FolderLoadTree.js';
import PipelineClone from '../../../../../models/pipelines/PipelineClone.js';
import CloneForm from '../../../../pipelines/browser/forms/CloneForm.jsx';

export type PipelineCloneModalProps = ActionModalBaseProps & {
  pipelineId: number;
};

function PipelineCloneModal(props: PipelineCloneModalProps) {
  const {open, onClose, disabled, pipelineId} = props;
  const [pending, setPending] = useState(false);
  const [selectedFolder, setSelectedFolder] = useState<Record<string, unknown> | null>(null);
  const formRef = useRef<FormInstance | null>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const {data: pipeline} = useQuery(pipelineQueryOptions(pipelineId, {enabled: false}));

  const handleClone = useCallback(
    async (parentId: number | null, name: string) => {
      setPending(true);
      try {
        const request = new PipelineClone(pipelineId, parentId, name);
        await request.send({});
        if (request.error) {
          message.error(request.error);
          return;
        }
        await Promise.all([
          queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
          queryClient.invalidateQueries({queryKey: pipelinesKeys.all}),
          parentId
            ? queryClient.invalidateQueries({queryKey: folderKeys.detail(parentId)})
            : Promise.resolve(),
        ]);
        const newPipelineId = request.value?.id;
        onClose?.();
        navigate(newPipelineId ? `/${newPipelineId}` : '/library');
      } finally {
        setPending(false);
      }
    },
    [pipelineId, queryClient, onClose, navigate],
  );

  const handleCancel = useCallback(() => {
    onClose?.();
  }, [onClose]);

  const canWrite = selectedFolder
    ? roleModel.writeAllowed(selectedFolder)
    : roleModel.writeAllowed(pipelinesLibrary.value);

  const cloneLabel = selectedFolder
    ? `Clone into '${(selectedFolder as {name?: string}).name ?? ''}'`
    : 'Clone into Library';

  const isPending = Boolean(pending || disabled);

  const footer = (
    <div style={{display: 'flex', justifyContent: 'flex-end', gap: 8}}>
      <Button id="folder-clone-form-cancel-button" onClick={handleCancel}>
        Cancel
      </Button>
      <Button
        id="folder-clone-form-ok-button"
        type="primary"
        disabled={!canWrite || isPending}
        onClick={() => formRef.current?.submit()}
      >
        {cloneLabel}
      </Button>
    </div>
  );

  return (
    <Modal
      open={open}
      title="Select destination folder"
      width="50%"
      closable={!isPending}
      onCancel={handleCancel}
      footer={footer}
      destroyOnHidden
    >
      <LegacyMobXStoresProvider>
        <CloneForm
          parentId={pipeline?.parentFolderId}
          visible={open}
          pending={isPending}
          onSubmit={handleClone}
          onFolderChange={setSelectedFolder}
          formRef={formRef}
        />
      </LegacyMobXStoresProvider>
    </Modal>
  );
}

export {PipelineCloneModal};
