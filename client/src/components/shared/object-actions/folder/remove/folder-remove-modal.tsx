import {useState, useEffect, useCallback, MouseEvent, KeyboardEvent} from 'react';
import {Checkbox} from 'antd';
import {deleteFolder, loadFolder} from '../../../../../api';
import {folderKeys} from '../../../../../queries';
import {
  RemoveObjectModal,
  RemovableObject,
} from '../../base/remove-object-modal/remove-object-modal.tsx';
import {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';
import {Folder} from '../../../../../@types/library.ts';
import type {UserInfo} from '../../../../../@types/users.ts';

type FolderRemoveModalProps = ActionModalBaseProps & {
  folder: Folder | number;
  onRemove?: (event: MouseEvent | KeyboardEvent) => void;
};

type FolderWithParent = Folder & RemovableObject;

function isFolderEmpty(f: Folder): boolean {
  return (
    (!f.childFolders || f.childFolders.length === 0) &&
    (!f.pipelines || f.pipelines.length === 0) &&
    (!f.storages || f.storages.length === 0) &&
    (!f.configurations || f.configurations.length === 0)
  );
}

async function loadFolderWithParent(id: number): Promise<FolderWithParent> {
  const f = await loadFolder(id);
  return {
    ...f,
    parent: f.parentId !== undefined ? {id: f.parentId, aclClass: 'FOLDER'} : undefined,
  };
}

function FolderRemoveModal(props: FolderRemoveModalProps) {
  const {folder, open, onRemove, ...rest} = props;
  const folderId = typeof folder === 'number' ? folder : folder.id;

  const handleRemove = useCallback(
    (event: MouseEvent | KeyboardEvent) => onRemove?.(event),
    [onRemove],
  );

  const [force, setForce] = useState(false);

  useEffect(() => {
    if (!open) setForce(false);
  }, [open]);

  const deleteFn = useCallback(
    (id: number | undefined) => deleteFolder(id!, force),
    [force],
  );

  const canRemove = useCallback(
    (_user: UserInfo, f: FolderWithParent) => isFolderEmpty(f) || force,
    [force],
  );

  return (
    <RemoveObjectModal
      {...rest}
      open={open}
      obj={folderId}
      onRemove={handleRemove}
      loadFn={loadFolderWithParent}
      deleteFn={deleteFn}
      canRemove={canRemove}
      queryKey={folderKeys.detail}
      title={(f: FolderWithParent) => (
        <span>
          Are you sure you want to delete folder <b>{f.name}</b>?
        </span>
      )}
    >
      {(f: FolderWithParent) => (
        <>
          <p>This operation cannot be undone.</p>
          {!isFolderEmpty(f) && (
            <Checkbox checked={force} onChange={(e) => setForce(e.target.checked)}>
              Delete sub-items
            </Checkbox>
          )}
        </>
      )}
    </RemoveObjectModal>
  );
}

export {FolderRemoveModal};
