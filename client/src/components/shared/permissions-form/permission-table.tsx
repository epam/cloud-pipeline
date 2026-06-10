import React from 'react';
import {Checkbox, Table} from 'antd';
import roleModel from '../../../utils/roleModel.jsx';
import {isNonNullable} from '../../../utilities/guards.ts';
import type {PermissionsFormController} from './use-permissions-form-controller.ts';
import {PERMISSION_COLUMNS, PERMISSIONS} from './types.ts';
import styles from './permissions-form.module.css';

type PermRow = {
  permission: string;
  allowMask: number;
  denyMask: number;
  allowed: boolean;
  denied: boolean;
  isRead?: boolean;
};

interface PermissionTableProps {
  ctrl: PermissionsFormController;
}

export function PermissionTable({ctrl}: PermissionTableProps) {
  const sel = ctrl.selectedPermission;
  if (!sel) return null;

  const {name, principal} = sel.sid;
  const enabledMask = ctrl.getEnabledMaskForSubject(name, principal);

  const columns = [
    {
      title: 'Permissions',
      dataIndex: 'permission',
      render: (permName: string, item: PermRow) => {
        if (!item.allowed && !item.denied) {
          return (
            <span>
              {permName} <i style={{fontSize: 'smaller'}}>(inherit)</i>
            </span>
          );
        }
        return permName;
      },
    } satisfies object,
    ctrl.permissionsColumns.includes(PERMISSION_COLUMNS.allow)
      ? ({
          title: 'Allow',
          width: 50,
          className: styles.userAllowDenyActions,
          render: (item: PermRow) => (
            <Checkbox
              disabled={ctrl.pending || ctrl.readonly || (item.allowMask & enabledMask) === 0}
              checked={item.allowed}
              onChange={ctrl.onAllowDenyValueChanged(
                item.allowMask | item.denyMask,
                item.allowMask,
                !item.isRead,
              )}
            />
          ),
        } satisfies object)
      : null,
    ctrl.permissionsColumns.includes(PERMISSION_COLUMNS.deny)
      ? ({
          title: 'Deny',
          width: 50,
          className: styles.userAllowDenyActions,
          render: (item: PermRow) => (
            <Checkbox
              disabled={ctrl.pending || ctrl.readonly || (item.denyMask & enabledMask) === 0}
              checked={item.denied}
              onChange={ctrl.onAllowDenyValueChanged(item.allowMask | item.denyMask, item.denyMask)}
            />
          ),
        } satisfies object)
      : null,
  ].filter(isNonNullable);

  const data: PermRow[] = [
    ctrl.availablePermissions.includes(PERMISSIONS.read)
      ? {
          permission: 'Read',
          allowMask: 1,
          denyMask: 1 << 1,
          allowed: roleModel.readAllowed(sel, true),
          denied: roleModel.readDenied(sel, true),
          isRead: true,
        }
      : null,
    ctrl.availablePermissions.includes(PERMISSIONS.write)
      ? {
          permission: 'Write',
          allowMask: 1 << 2,
          denyMask: 1 << 3,
          allowed: roleModel.writeAllowed(sel, true),
          denied: roleModel.writeDenied(sel, true),
        }
      : null,
    ctrl.availablePermissions.includes(PERMISSIONS.execute)
      ? {
          permission: 'Execute',
          allowMask: 1 << 4,
          denyMask: 1 << 5,
          allowed: roleModel.executeAllowed(sel, true),
          denied: roleModel.executeDenied(sel, true),
        }
      : null,
  ].filter((r): r is PermRow => r !== null);

  return (
    <Table<PermRow>
      style={{marginTop: 10}}
      loading={ctrl.pending}
      showHeader
      size="small"
      columns={columns}
      pagination={false}
      rowKey={(item) => item.permission}
      dataSource={data}
    />
  );
}
