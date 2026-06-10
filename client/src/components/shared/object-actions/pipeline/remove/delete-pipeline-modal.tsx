import {Button, Modal} from 'antd';
import {useCallback, MouseEvent, KeyboardEvent, useState} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';
import {folderKeys, libraryTreeKeys, pipelineQueryOptions} from '../../../../../queries';
import cloudPipelineApi from '../../../../../api/cloud-pipeline-api.ts';

function preventDefault(event?: MouseEvent | KeyboardEvent) {
  if (event) {
    event.stopPropagation();
    event.preventDefault();
  }
}

export type DeletePipelineModalProps = ActionModalBaseProps & {
  pipelineId: number;
  isVersionedStorage?: boolean;
  onDone?: (event: MouseEvent | KeyboardEvent) => void;
};

function DeletePipelineModal(props: DeletePipelineModalProps) {
  const {open, pipelineId, isVersionedStorage, onClose, onDone, disabled} = props;
  const queryClient = useQueryClient();
  const {data: pipeline} = useQuery(pipelineQueryOptions(pipelineId, {enabled: false}));
  const [deletePending, setDeletePending] = useState(false);

  const objectLabel = isVersionedStorage ? 'versioned storage' : 'pipeline';

  const onDelete = useCallback(
    async (keepRepository: boolean, event: MouseEvent) => {
      preventDefault(event);
      try {
        setDeletePending(true);
        await cloudPipelineApi.jsonDelete({
          uri: `pipeline/${pipelineId}/delete`,
          query: {keep_repository: String(keepRepository)},
        });
        const parentFolderId = pipeline?.parentFolderId;
        await Promise.all([
          queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
          parentFolderId
            ? queryClient.invalidateQueries({queryKey: folderKeys.detail(parentFolderId)})
            : Promise.resolve(),
        ]);
        const done = onDone ?? onClose;
        done?.(event);
      } catch {
        // keep modal open on error
      } finally {
        setDeletePending(false);
      }
    },
    [pipelineId, pipeline, queryClient, onDone, onClose],
  );

  const onCloseWrapper = useCallback(
    (event: MouseEvent | KeyboardEvent) => {
      preventDefault(event);
      onClose?.(event);
    },
    [onClose],
  );

  const title = (
    <span style={{paddingRight: 25, display: 'flex'}}>
      {`Do you want to delete a ${objectLabel} with repository or only unregister it?`}
    </span>
  );

  return (
    <Modal
      open={open}
      onCancel={onCloseWrapper}
      title={title}
      footer={
        <div className="cp-modal-footer-actions cp-modal-footer-actions--split">
          <div className="cp-modal-footer-actions-group">
            <Button
              id="edit-pipeline-delete-dialog-cancel-button"
              disabled={deletePending || disabled}
              onClick={onCloseWrapper}
            >
              Cancel
            </Button>
          </div>
          <div className="cp-modal-footer-actions-group cp-modal-footer-actions-group--end">
            <Button
              id="edit-pipeline-delete-dialog-unregister-button"
              danger
              disabled={deletePending || disabled}
              onClick={(e) => onDelete(true, e)}
            >
              Unregister
            </Button>
            <Button
              id="edit-pipeline-delete-dialog-delete-button"
              danger
              disabled={deletePending || disabled}
              onClick={(e) => onDelete(false, e)}
            >
              Delete
            </Button>
          </div>
        </div>
      }
    >
      <p>This operation cannot be undone.</p>
    </Modal>
  );
}

export {DeletePipelineModal};
export default DeletePipelineModal;
