/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import {
  Alert,
  Table,
  Row,
  Input,
  Icon,
  Button,
  Modal,
  message
} from 'antd';
import RoleRemove from '../../../models/user/RoleRemove';
import EditRoleDialog from '../forms/EditRoleDialog';
import LoadingView from '../../special/LoadingView';
import roleModel from '../../../utils/roleModel';
import CreateGroupDialog from './create-group-dialog';
import {
  alphabeticSorter,
  capitalized,
  getRoleType,
  splitRoleName
} from './utilities';
import styles from '../UserManagementForm.css';

const PAGE_SIZE = 20;

@roleModel.authenticationInfo
@inject('roles')
@observer
export default class GroupsManagement extends React.Component {
  static propTypes = {
    predefined: PropTypes.bool
  };

  state = {
    groupsSearchText: null,
    groupsTableCurrentPage: 1,
    groupsTableFilter: null,
    groupsTableSorter: null,
    editableGroup: null,
    createGroupDialogVisible: null
  };

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.predefined !== this.props.predefined) {
      this.prepare();
    }
  }

  prepare () {
    this.setState({
      groupsTableCurrentPage: 1,
      groupsSearchText: undefined
    });
  }

  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value.admin
      : false;
  };

  get isReader () {
    return roleModel.hasRole('ROLE_USER_READER')(this);
  };

  handleGroupsTableChange = (pagination, filter, sorter) => {
    const {current} = pagination;
    this.setState({
      groupsTableCurrentPage: current,
      groupsTableFilter: filter,
      groupsTableSorter: sorter
    });
  };

  @computed
  get roles () {
    const {roles, predefined = false} = this.props;
    if (roles.loaded) {
      return (roles.value || [])
        .map(r => ({
          ...r,
          displayName: splitRoleName(r)
        }))
        .filter(r => r.predefined === predefined);
    }
    return [];
  }

  @computed
  get rolesPending () {
    const {roles} = this.props;
    return roles.pending;
  }

  get filteredRoles () {
    const {groupsSearchText} = this.state;
    return this.roles
      .filter(r => !groupsSearchText ||
        !groupsSearchText.length ||
        r.displayName
          .toLowerCase()
          .includes(groupsSearchText.toLowerCase())
      );
  }

  reload = () => {
    this.props.roles.fetch();
  };

  onGroupSearchChanged = (e) => {
    const search = e ? (e.target.value || undefined) : undefined;
    if (this.state.groupsSearchText !== search) {
      this.setState({
        groupsTableCurrentPage: 1,
        groupsSearchText: search
      });
    }
  };

  alphabeticRoleNameSorter = (a, b) => {
    return alphabeticSorter(a.name, b.name);
  };

  openEditGroupDialog = (role) => {
    this.setState({editableGroup: role});
  };

  closeEditGroupDialog = () => {
    this.setState({editableGroup: null}, this.reload);
  };

  deleteRoleConfirm = (e, role) => {
    e.stopPropagation();
    const deleteRole = async () => {
      const hide = message.loading(`Removing ${getRoleType(role)}...`, 0);
      const request = new RoleRemove(role.id);
      await request.send({});
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        return this.reload();
      }
    };
    Modal.confirm({
      title: `Are you sure you want to delete ${getRoleType(role)} '${splitRoleName(role)}'?`,
      content: 'This operation cannot be undone.',
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        return deleteRole();
      }
    });
  };

  renderGroupsTable = () => {
    if (!this.props.roles.loaded && this.rolesPending) {
      return <LoadingView />;
    }
    const {predefined} = this.props;
    const columns = [
      {
        dataIndex: 'displayName',
        key: 'name',
        title: capitalized(getRoleType({predefined})),
        sorter: this.alphabeticRoleNameSorter,
        render: (name, group) => {
          let blockedSpan;
          if (group.blocked) {
            blockedSpan = (
              <span
                style={{fontStyle: 'italic', marginLeft: 5}}
              >
                - blocked
              </span>
            );
          }
          return (
            <span>
              {name}
              {blockedSpan}
            </span>
          );
        }
      },
      this.isAdmin
        ? {
          key: 'actions',
          render: (role) => {
            return (
              <Row className={styles.roleActions} type="flex" justify="end">
                <Button size="small" onClick={() => this.openEditGroupDialog(role)}>
                  <Icon type="edit" />
                </Button>
                {
                  !predefined && (
                    <Button
                      size="small"
                      type="danger"
                      onClick={(e) => this.deleteRoleConfirm(e, role)}
                    >
                      <Icon type="delete" />
                    </Button>
                  )
                }
              </Row>
            );
          }
        }
        : undefined
    ].filter(Boolean);
    return (
      <Table
        className={styles.table}
        rowKey="id"
        loading={this.rolesPending}
        columns={columns}
        dataSource={this.filteredRoles}
        onChange={this.handleGroupsTableChange}
        onRowClick={(group) => this.openEditGroupDialog(group)}
        pagination={{
          total: this.filteredRoles.length,
          PAGE_SIZE,
          current: this.state.groupsTableCurrentPage
        }}
        size="small" />
    );
  };

  openCreateGroupDialog = () => {
    this.setState({
      createGroupDialogVisible: true
    });
  };

  closeCreateGroupDialog = (updated = false) => {
    this.setState({
      createGroupDialogVisible: false
    }, () => {
      if (updated) {
        this.reload();
      }
    });
  };

  render () {
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return null;
    }
    if (!this.isReader && !this.isAdmin) {
      return (
        <Alert type="error" message="Access is denied" />
      );
    }
    const {predefined} = this.props;
    return (
      <div className={styles.container}>
        <Row type="flex" style={{marginBottom: 10}}>
          <div style={{flex: 1}}>
            <Input.Search
              id="search-groups-input"
              placeholder={`Search ${getRoleType({predefined})}s`}
              style={{width: '100%'}}
              value={this.state.groupsSearchText}
              onChange={this.onGroupSearchChanged} />
          </div>
          {
            this.isAdmin && !predefined && (
              <div style={{paddingLeft: 10}}>
                <Button
                  type="primary"
                  onClick={this.openCreateGroupDialog}
                >
                  <Icon type="plus" /> Create group
                </Button>
              </div>
            )
          }
        </Row>
        {this.renderGroupsTable()}
        <EditRoleDialog
          visible={!!this.state.editableGroup}
          onClose={this.closeEditGroupDialog}
          role={this.state.editableGroup}
          readOnly={!this.isAdmin}
        />
        <CreateGroupDialog
          visible={this.state.createGroupDialogVisible}
          onClose={this.closeCreateGroupDialog}
        />
      </div>
    );
  }

  componentDidMount () {
    this.props.roles.fetch();
  }
}
