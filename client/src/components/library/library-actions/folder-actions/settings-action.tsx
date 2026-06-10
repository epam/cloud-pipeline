import {useCallback, useMemo, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Button, Dropdown} from 'antd';
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
import {folderQueryOptions} from '../../../../queries';
import roleModel from '../../../../utils/roleModel.jsx';
import {SETTINGS_ACTION_KEYS} from './folder-action-keys.ts';
import {
  mockDeleteFolderConfirm,
  mockLockUnlockFolderConfirm,
  mockOpenCloneFolderDialog,
  mockOpenRenameFolderDialog,
} from './folder-action-mocks.ts';
import {useFolderManagerRoles} from './folder-action-roles.ts';
import {asAntdMenuItems, type FolderActionMenuItems} from './folder-action-types.ts';

type SettingsActionProps = CommonProps & {
  folderId: number;
  readOnly?: boolean;
};

function SettingsAction(props: SettingsActionProps) {
  const {folderId, readOnly = false} = props;
  const [open, setOpen] = useState(false);
  const {data: folder} = useQuery(folderQueryOptions(folderId));
  const roles = useFolderManagerRoles();

  const onClick = useCallback<NonNullable<MenuProps['onClick']>>(
    ({key}) => {
      setOpen(false);
      if (!folder) {
        return;
      }
      switch (key) {
        case SETTINGS_ACTION_KEYS.edit:
          mockOpenRenameFolderDialog(folder);
          break;
        case SETTINGS_ACTION_KEYS.clone:
          mockOpenCloneFolderDialog(folder.id);
          break;
        case SETTINGS_ACTION_KEYS.lock:
          mockLockUnlockFolderConfirm(folder, true);
          break;
        case SETTINGS_ACTION_KEYS.unlock:
          mockLockUnlockFolderConfirm(folder, false);
          break;
        case SETTINGS_ACTION_KEYS.delete:
          mockDeleteFolderConfirm(folder);
          break;
        default:
          break;
      }
    },
    [folder],
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
  );
}

export {SettingsAction};
