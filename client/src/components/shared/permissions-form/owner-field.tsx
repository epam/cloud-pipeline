import React from 'react';
import {AutoComplete} from 'antd';
import type {UserInfo} from '../../../@types/users.ts';
import type {PermissionsFormController} from './use-permissions-form-controller.ts';
import styles from './permissions-form.module.css';

function renderUserDisplayName(user: UserInfo) {
  if (user.attributes && Object.keys(user.attributes).length > 0) {
    const attributesString = Object.values(user.attributes).join(', ');
    return (
      <div style={{display: 'flex', flexDirection: 'column'}}>
        <div>{user.userName}</div>
        <div>
          <span style={{fontSize: 'smaller'}}>{attributesString}</span>
        </div>
      </div>
    );
  }
  return user.userName;
}

interface OwnerFieldProps {
  ctrl: PermissionsFormController;
}

export function OwnerField({ctrl}: OwnerFieldProps) {
  if (ctrl.pending || ctrl.error || !ctrl.originalOwner || !ctrl.showOwner) return null;

  if (ctrl.isAdminOrOwner) {
    return (
      <div className={styles.ownerContainer}>
        <span style={{marginRight: 5}}>Owner: </span>
        <AutoComplete
          size="small"
          style={{flex: 1}}
          placeholder="Change owner"
          value={ctrl.ownerInput === undefined ? ctrl.owner : ctrl.ownerInput}
          onBlur={() => ctrl.setOwnerInput(undefined)}
          onSelect={ctrl.onOwnerSelect}
          showSearch={{
            onSearch: ctrl.findOwnerUser,
          }}
          options={ctrl.fetchedUsers.map((user) => ({
            value: String(user.id),
            label: renderUserDisplayName(user),
          }))}
        />
      </div>
    );
  }

  return (
    <div className={styles.ownerContainer}>
      <span style={{marginRight: 5}}>Owner: </span>
      <b id="object-owner" style={{paddingLeft: 4}}>
        {ctrl.owner}
      </b>
    </div>
  );
}
