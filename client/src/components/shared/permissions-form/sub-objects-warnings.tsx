import React from 'react';
import {Alert, Popover} from 'antd';
import roleModel from '../../../utils/roleModel.jsx';
import type {PermissionsFormController} from './use-permissions-form-controller.ts';
import type {PermissionsFormProps} from './types.ts';

const MAX_SUB_OBJECTS_WARNINGS_TO_SHOW = 5;

function plural(count: number, noun: string) {
  return `${noun}${count > 1 ? 's' : ''}`;
}

interface SubObjectsWarningsProps {
  ctrl: PermissionsFormController;
  subObjectsPermissionsErrorTitle: PermissionsFormProps['subObjectsPermissionsErrorTitle'];
}

export function SubObjectsWarnings({
  ctrl,
  subObjectsPermissionsErrorTitle,
}: SubObjectsWarningsProps) {
  const check = {
    read: roleModel.readPermissionEnabled(ctrl.subObjectsPermissionsMaskToCheck),
    write: roleModel.writePermissionEnabled(ctrl.subObjectsPermissionsMaskToCheck),
    execute: roleModel.executePermissionEnabled(ctrl.subObjectsPermissionsMaskToCheck),
  };

  const warnings: React.ReactNode[] = [];
  for (const {mask, sid = {name: '', principal: false}} of ctrl.permissions) {
    const maskToCheck = mask & ctrl.subObjectsPermissionsMaskToCheck;
    const {name, principal} = sid;
    const rolesToCheck: {name: string; principal: boolean}[] = [];
    if (principal) {
      const userInfo = ctrl.allUsers.find((u) => u.userName === name);
      if (userInfo?.roles) {
        rolesToCheck.push(...userInfo.roles.map((r) => ({name: r.name, principal: false})));
      }
    } else {
      rolesToCheck.push({name: 'ROLE_USER', principal: false});
    }
    for (const subObjPerm of ctrl.subObjectsPermissions) {
      const {read, write, execute} = roleModel.checkObjectPermissionsConflict(
        maskToCheck,
        sid,
        rolesToCheck,
        subObjPerm.owner ?? '',
        subObjPerm.permissions,
      );
      if (check.read && read) {
        warnings.push(
          <span>
            {subObjPerm.object.name}: read denied for <b>{name}</b>
          </span>,
        );
      }
      if (check.write && write) {
        warnings.push(
          <span>
            {subObjPerm.object.name}: write denied for <b>{name}</b>
          </span>,
        );
      }
      if (check.execute && execute) {
        warnings.push(
          <span>
            {subObjPerm.object.name}: execute denied for <b>{name}</b>
          </span>,
        );
      }
    }
  }

  if (warnings.length === 0) return null;

  const title = subObjectsPermissionsErrorTitle && (
    <div style={{marginBottom: 5}}>{subObjectsPermissionsErrorTitle}</div>
  );
  const content = (
    <div>
      {warnings.map((w, i) => (
        <div key={i}>{w}</div>
      ))}
    </div>
  );

  if (warnings.length > MAX_SUB_OBJECTS_WARNINGS_TO_SHOW) {
    const rest = warnings.length - MAX_SUB_OBJECTS_WARNINGS_TO_SHOW;
    return (
      <Alert
        showIcon
        style={{marginBottom: 5}}
        type="warning"
        title={title}
        description={
          <div>
            {warnings.slice(0, MAX_SUB_OBJECTS_WARNINGS_TO_SHOW).map((w, i) => (
              <div key={i}>{w}</div>
            ))}
            <div>
              <Popover content={content}>
                <a>
                  ... and {rest} more {plural(rest, 'warning')}
                </a>
              </Popover>
            </div>
          </div>
        }
      />
    );
  }

  return (
    <Alert showIcon style={{marginBottom: 5}} type="warning" title={title} description={content} />
  );
}
