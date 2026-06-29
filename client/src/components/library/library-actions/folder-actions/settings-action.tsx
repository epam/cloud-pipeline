import {useCallback, useMemo, useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {Button, Dropdown, Modal} from 'antd';
import {message} from 'antd';
import type {MenuProps} from 'antd';
import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  LockOutlined,
  SettingOutlined,
  UnlockOutlined,
} from '@ant-design/icons';

import type {CommonProps} from '../../../../@types/common.ts';
import {folderKeys, folderQueryOptions, libraryTreeKeys, queryClient} from '../../../../queries';
import {FolderRemoveModal} from '../../../shared/object-actions/folder/remove/folder-remove-modal.tsx';
import roleModel from '../../../../utils/roleModel.jsx';
import EditFolderForm from '../../../pipelines/browser/forms/EditFolderForm.jsx';
import CloneFormWithModal from '../../../pipelines/browser/forms/CloneFormWithModal.jsx';
import {LegacyMobXStoresProvider} from '../../../../pages/_shared/legacy-mobx-stores-provider.tsx';
import FolderUpdate from '../../../../models/folders/FolderUpdate.js';
import FolderClone from '../../../../models/folders/FolderClone.js';
import {FolderLock, FolderUnLock} from '../../../../models/folders/FolderLock.js';

import {SETTINGS_ACTION_KEYS} from './folder-action-keys.ts';
import {useFolderManagerRoles} from './folder-action-roles.ts';
import {asAntdMenuItems, type FolderActionMenuItems} from './folder-action-types.ts';

type SettingsActionProps = CommonProps & {
  folderId: number;
  readOnly?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {folderId, readOnly = false} = props;
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const {data: folder} = useQuery(folderQueryOptions(folderId));
  const roles = useFolderManagerRoles();

  // ── Rename ──────────────────────────────────────────────────────────────
  const [renameFolderVisible, setRenameFolderVisible] = useState(false);
  const [renameFolderPending, setRenameFolderPending] = useState(false);
  const renameFolderRequest = useRef(new FolderUpdate());

  const renameFolder = useCallback(
    async ({name}: {name: string}) => {
      if (!folder) return;
      if (name === folder.name) {
        setRenameFolderVisible(false);
        return;
      }
      const hide = message.loading('Renaming folder...', 0);
      await renameFolderRequest.current.send({id: folder.id, parentId: folder.parentId, name});
      hide();
      if (renameFolderRequest.current.error) {
        message.error(renameFolderRequest.current.error, 5);
        return;
      }
      setRenameFolderVisible(false);
      await Promise.all([
        queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)}),
        queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
      ]);
    },
    [folder, folderId],
  );

  const handleSubmitRenameFolder = useCallback(
    (values: {name: string}) => {
      setRenameFolderPending(true);
      renameFolder(values).finally(() => setRenameFolderPending(false));
    },
    [renameFolder],
  );

  // ── Clone ────────────────────────────────────────────────────────────────
  const [cloneVisible, setCloneVisible] = useState(false);
  const [clonePending, setClonePending] = useState(false);

  const cloneFolder = useCallback(
    async (parentId: number | null, name: string) => {
      const hide = message.loading('Cloning folder...', 0);
      const request = new FolderClone(folderId, parentId, name);
      await request.send({});
      hide();
      if (request.error) {
        message.error(request.error, 5);
        return;
      }
      setCloneVisible(false);
      await Promise.all([
        ...(parentId !== null
          ? [queryClient.invalidateQueries({queryKey: folderKeys.detail(parentId)})]
          : []),
        queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
      ]);
      const newId = request.value?.id;
      if (newId) {
        navigate(`/folder/${newId}`);
      } else {
        navigate('/library');
      }
    },
    [folderId, navigate],
  );

  const handleSubmitClone = useCallback(
    (parentId: number | null, name: string) => {
      setClonePending(true);
      cloneFolder(parentId, name).finally(() => setClonePending(false));
    },
    [cloneFolder],
  );

  // ── Lock / Unlock ────────────────────────────────────────────────────────
  const lockUnlock = useCallback(
    (lock: boolean) => {
      if (!folder) return;
      Modal.confirm({
        title: `Are you sure you want to ${lock ? 'lock' : 'unlock'} folder '${folder.name}'?`,
        style: {wordWrap: 'break-word'},
        onOk: async () => {
          const hide = message.loading(lock ? 'Locking folder...' : 'Unlocking folder...', 0);
          const request = lock ? new FolderLock(folderId) : new FolderUnLock(folderId);
          await request.send({});
          hide();
          if (request.error) {
            message.error(request.error, 5);
            return;
          }
          await Promise.all([
            queryClient.invalidateQueries({queryKey: folderKeys.detail(folderId)}),
            queryClient.invalidateQueries({queryKey: libraryTreeKeys.all}),
          ]);
        },
      });
    },
    [folder, folderId],
  );

  // ── Delete ───────────────────────────────────────────────────────────────
  const [deleteFolderVisible, setDeleteFolderVisible] = useState(false);

  const handleFolderDeleted = useCallback(() => {
    setDeleteFolderVisible(false);
    const parentId = folder?.parentId;
    if (parentId) {
      navigate(`/folder/${parentId}`);
    } else {
      navigate('/library');
    }
  }, [folder?.parentId, navigate]);

  // ── Menu click ───────────────────────────────────────────────────────────
  const onClick = useCallback<NonNullable<MenuProps['onClick']>>(
    ({key}) => {
      setOpen(false);
      if (!folder) return;
      switch (key) {
        case SETTINGS_ACTION_KEYS.edit:
          setRenameFolderVisible(true);
          break;
        case SETTINGS_ACTION_KEYS.clone:
          setCloneVisible(true);
          break;
        case SETTINGS_ACTION_KEYS.lock:
          lockUnlock(true);
          break;
        case SETTINGS_ACTION_KEYS.unlock:
          lockUnlock(false);
          break;
        case SETTINGS_ACTION_KEYS.delete:
          setDeleteFolderVisible(true);
          break;
        default:
          break;
      }
    },
    [folder, lockUnlock],
  );

  const items = useMemo(() => {
    if (!folder) {
      return [];
    }

    const editActions: FolderActionMenuItems = [];

    // Folder.jsx: roleModel.readAllowed(folder)
    if (roleModel.readAllowed(folder)) {
      editActions.push({
        id: 'edit-folder-button',
        key: SETTINGS_ACTION_KEYS.edit,
        label: (
          <span>
            <EditOutlined style={{marginRight: 5}} />
            {roleModel.writeAllowed(folder) ? 'Edit folder' : 'Permissions'}
          </span>
        ),
      });
    }

    // Folder.jsx: !readOnly && roleModel.isOwner(folder)
    if (!readOnly && roleModel.isOwner(folder)) {
      editActions.push({
        key: SETTINGS_ACTION_KEYS.clone,
        id: 'clone-folder-button',
        label: (
          <span>
            <CopyOutlined /> Clone
          </span>
        ),
      });
    }

    const folderIsReadOnly = !!folder.locked;
    // Folder.jsx: folder.locked && roleModel.isOwner(folder)
    if (folderIsReadOnly && roleModel.isOwner(folder)) {
      editActions.push({
        id: 'unlock-button',
        key: SETTINGS_ACTION_KEYS.unlock,
        label: (
          <span>
            <UnlockOutlined /> Unlock
          </span>
        ),
      });
    } else if (!folderIsReadOnly && roleModel.writeAllowed(folder)) {
      // Folder.jsx: !folder.locked && roleModel.writeAllowed(folder)
      editActions.push({
        id: 'lock-button',
        key: SETTINGS_ACTION_KEYS.lock,
        label: (
          <span>
            <LockOutlined /> Lock
          </span>
        ),
      });
    }

    // Folder.jsx: !readOnly && (roleModel.isManager.storageAdmin || (roleModel.writeAllowed(folder) && roleModel.isManager.folder))
    const canDelete =
      !readOnly &&
      (roles.isStorageAdmin || (roleModel.writeAllowed(folder) && roles.isFolderManager));
    if (canDelete) {
      if (editActions.length > 0) {
        editActions.push({type: 'divider', key: 'divider'});
      }
      editActions.push({
        id: 'delete-folder-button',
        key: SETTINGS_ACTION_KEYS.delete,
        label: (
          <span className="cp-danger">
            <DeleteOutlined /> Delete
          </span>
        ),
      });
    }

    return editActions;
  }, [folder, readOnly, roles.isFolderManager, roles.isStorageAdmin]);

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
        menu={{items: asAntdMenuItems(items), onClick, style: {width: 150}}}
      >
        <Button key="edit" id="edit-folder-menu-button" size="small">
          <SettingOutlined />
        </Button>
      </Dropdown>
      <LegacyMobXStoresProvider>
        <EditFolderForm
          visible={renameFolderVisible}
          title={roleModel.writeAllowed(folder) ? 'Edit folder' : 'Permissions'}
          name={folder?.name}
          folderId={folder?.id}
          mask={folder?.mask}
          locked={folder?.locked}
          pending={renameFolderPending}
          onSubmit={handleSubmitRenameFolder}
          onCancel={() => setRenameFolderVisible(false)}
        />
        <CloneFormWithModal
          parentId={folder?.parentId}
          visible={cloneVisible}
          pending={clonePending}
          onCancel={() => setCloneVisible(false)}
          onSubmit={handleSubmitClone}
        />
      </LegacyMobXStoresProvider>
      <FolderRemoveModal
        folder={folderId}
        open={deleteFolderVisible}
        onClose={() => setDeleteFolderVisible(false)}
        onRemove={handleFolderDeleted}
      />
    </>
  );
}

export {SettingsAction};
