import React, {useCallback} from 'react';
import {AutoComplete, Button, Modal, Select} from 'antd';
import UserName from '../user-name';
import styles from './permissions-form.module.css';
import {usePermissionsFormController} from './use-permissions-form-controller.ts';
import type {PermissionsFormProps} from './types.ts';
import {OwnerField} from './owner-field.tsx';
import {SubObjectsWarnings} from './sub-objects-warnings.tsx';
import {UsersTable} from './users-table.tsx';

export function PermissionsForm(props: PermissionsFormProps) {
  const ctrl = usePermissionsFormController(props);
  const {subObjectsPermissionsErrorTitle} = props;

  const onSearch = useCallback(
    (value: string) => ctrl.setSearchUserTouched(value.length > 2),
    [ctrl],
  );
  const filterOption = useCallback(
    (input: string, option?: {attributes?: string[]}) => {
      const attributes = option?.attributes;
      if (!attributes) return false;
      return attributes
        .map((o) => `${o}`.toLowerCase())
        .some((o) => o.includes((input || '').toLowerCase()));
    },
    [ctrl],
  );

  return (
    <div className={styles.permissionsForm}>
      <OwnerField ctrl={ctrl} />
      <SubObjectsWarnings
        ctrl={ctrl}
        subObjectsPermissionsErrorTitle={subObjectsPermissionsErrorTitle}
      />
      <UsersTable ctrl={ctrl} />
      {!ctrl.readonly && (
        <div className={styles.permissionsFormFooter}>
          <Button
            className={styles.permissionsFormAction}
            disabled={!ctrl.permissionsChanged || ctrl.pending}
            onClick={ctrl.revertChanges}
          >
            REVERT
          </Button>
          <Button
            className={styles.permissionsFormAction}
            disabled={!ctrl.permissionsChanged || ctrl.pending}
            type="primary"
            onClick={ctrl.applyChanges}
          >
            APPLY
          </Button>
        </div>
      )}

      <Modal
        title="Select user"
        onCancel={() => {
          ctrl.setSelectedUser(undefined);
          ctrl.setFindUserVisible(false);
        }}
        onOk={ctrl.onSelectUser}
        footer={
          <div className="cp-modal-footer-actions">
            <Button
              onClick={() => {
                ctrl.setSelectedUser(undefined);
                ctrl.setFindUserVisible(false);
              }}
            >
              Cancel
            </Button>
            <Button type="primary" disabled={ctrl.pending} onClick={ctrl.onSelectUser}>
              OK
            </Button>
          </div>
        }
        open={ctrl.findUserVisible}
      >
        <Select
          disabled={!ctrl.allUsersLoaded}
          placeholder="Enter the account info"
          style={{width: '100%'}}
          showSearch={{
            onSearch,
            filterOption,
          }}
          value={ctrl.selectedUser}
          onSelect={ctrl.setSelectedUser}
          onFocus={() => ctrl.setSearchUserTouched(false)}
          notFoundContent={ctrl.searchUserTouched ? 'Not found' : 'Start typing to filter users...'}
          options={
            ctrl.searchUserTouched
              ? ctrl.allUsers.map((user) => ({
                  value: user.userName,
                  label: <UserName userName={user.userName} />,
                  attributes: [user.userName, ...Object.values(user.attributes || {})] as string[],
                }))
              : []
          }
        />
      </Modal>

      <Modal
        title="Select group"
        onCancel={() => {
          ctrl.setFindGroupVisible(false);
        }}
        onOk={ctrl.onSelectGroup}
        footer={
          <div className="cp-modal-footer-actions">
            <Button onClick={() => ctrl.setFindGroupVisible(false)}>Cancel</Button>
            <Button type="primary" disabled={ctrl.pending} onClick={ctrl.onSelectGroup}>
              OK
            </Button>
          </div>
        }
        open={ctrl.findGroupVisible}
      >
        <AutoComplete
          value={ctrl.selectedGroupRef.current}
          style={{width: '100%'}}
          options={ctrl.findGroupDataSource().map((group) => ({value: group}))}
          onChange={ctrl.onGroupSearchChanged}
          placeholder="Enter the group name"
        />
      </Modal>
    </div>
  );
}
