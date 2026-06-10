import React from 'react';
import {Alert, Button, Table} from 'antd';
import {
  DeleteOutlined,
  TeamOutlined,
  UserAddOutlined,
  UserOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import classNames from 'classnames';
import UserName from '../user-name';
import {PermissionTable} from './permission-table.tsx';
import type {PermissionsFormController} from './use-permissions-form-controller.ts';
import type {Permission} from './types.ts';
import styles from './permissions-form.module.css';

interface UsersTableProps {
  ctrl: PermissionsFormController;
}

export function UsersTable({ctrl}: UsersTableProps) {
  if (ctrl.error) {
    return <Alert type="warning" title={ctrl.error} />;
  }

  const getSidName = (name: string, principal: boolean) => {
    if (principal) return <UserName userName={name} />;
    const role = ctrl.roles.find((r) => !r.predefined && r.name === name);
    return role ? ctrl.splitRoleName(name) : name;
  };

  const columns = [
    {
      key: 'icon',
      width: 20,
      className: styles.userIcon,
      render: (item: Permission) => (item.sid.principal ? <UserOutlined /> : <TeamOutlined />),
    },
    {
      dataIndex: ['sid', 'name'],
      key: 'name',
      render: (name: string, item: Permission) => getSidName(name, item.sid.principal),
    },
    {
      key: 'actions',
      className: styles.userActions,
      render: (item: Permission) => (
        <Button
          disabled={
            ctrl.pending ||
            ctrl.readonly ||
            ctrl.subjectIsReadOnly(item.sid.name, item.sid.principal)
          }
          onClick={ctrl.removeUserOrGroup(item)}
          size="small"
        >
          <DeleteOutlined />
        </Button>
      ),
    },
  ];

  const getRowClassName = (item: Permission) => {
    if (!ctrl.selectedPermission || ctrl.selectedPermission.sid.name !== item.sid.name) {
      return styles.row;
    }
    return classNames(styles.selectedRow, 'cp-edit-permissions-selected-row');
  };

  const tableTitle = () => (
    <div className={styles.tableTitle}>
      <span className={styles.tableTitleLabel}>
        <b>Groups and users</b>
      </span>
      <span className={styles.tableTitleActions}>
        <span className={styles.actions}>
          <Button
            disabled={ctrl.readonly || ctrl.permissionsAreReadOnly}
            size="small"
            onClick={() => ctrl.setFindUserVisible(true)}
          >
            <UserAddOutlined />
          </Button>
          <Button
            disabled={ctrl.readonly || ctrl.permissionsAreReadOnly}
            size="small"
            onClick={() => {
              ctrl.selectedGroupRef.current = undefined;
              ctrl.setFindGroupVisible(true);
            }}
          >
            <UsergroupAddOutlined />
          </Button>
        </span>
      </span>
    </div>
  );

  return (
    <div className={styles.permissionsTableSection}>
      <Table
        className={styles.table}
        scroll={{y: 200}}
        rowClassName={getRowClassName}
        onRow={(record) => ({onClick: () => ctrl.setSelectedPermission(record)})}
        loading={ctrl.pending}
        title={tableTitle}
        showHeader={false}
        size="small"
        columns={columns}
        pagination={false}
        rowKey={(item) => item.sid.name}
        dataSource={ctrl.permissions.map((p) => p)}
      />
      <PermissionTable ctrl={ctrl} />
    </div>
  );
}
