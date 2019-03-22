/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed, observable} from 'mobx';
import {
  Tabs,
  Table,
  Row,
  Checkbox,
  Input,
  Dropdown,
  Card,
  Icon,
  Button,
  Modal,
  message,
  Select
} from 'antd';
import Roles from '../../../models/user/Roles';
import UserFind from '../../../models/user/UserFind';
import RoleCreate from '../../../models/user/RoleCreate';
import RoleRemove from '../../../models/user/RoleRemove';
import UserCreate from '../../../models/user/UserCreate';
import UserDelete from '../../../models/user/UserDelete';
import EditUserRolesDialog from './forms/EditUserRolesDialog';
import CreateUserForm from './forms/CreateUserForm';
import EditRoleDialog from './forms/EditRoleDialog';
import LoadingView from '../../special/LoadingView';
import styles from './UserManagementForm.css';
import roleModel from '../../../utils/roleModel';

const PAGE_SIZE = 20;

@inject('dataStorages', 'users')
@inject(({users}) => ({
  users,
  roles: new Roles()
}))
@observer
export default class UserManagementForm extends React.Component {

  static propTypes = {
    onInitialized: PropTypes.func
  };

  state = {
    userSearchText: null,
    usersTableCurrentPage: 1,
    usersTableFilter: null,
    usersTableSorter: null,
    editableUser: null,
    rolesSearchText: null,
    rolesTableCurrentPage: 1,
    rolesTableFilter: null,
    rolesTableSorter: null,
    editableRole: null,
    createUserDialogVisible: false,
    groupsSearchText: null,
    groupsTableCurrentPage: 1,
    groupsTableFilter: null,
    groupsTableSorter: null,
    editableGroup: null,
    createGroupDialogVisible: null,
    createGroupName: null,
    createGroupDefault: false,
    createGroupDefaultDataStorage: null,
    operationInProgress: false
  };

  operationWrapper = (operation) => (...props) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await operation(...props);
      this.setState({
        operationInProgress: false
      });
    });
  };

  handleUserTableChange = (pagination, filter, sorter) => {
    const {current} = pagination;
    this.setState({
      usersTableCurrentPage: current,
      usersTableFilter: filter,
      usersTableSorter: sorter
    }); // todo: fetchUsers() when server-side sorting & pagination would be implemented
  };

  handleRolesTableChange = (pagination, filter, sorter) => {
    const {current} = pagination;
    this.setState({
      rolesTableCurrentPage: current,
      rolesTableFilter: filter,
      rolesTableSorter: sorter
    });
  };

  handleGroupsTableChange = (pagination, filter, sorter) => {
    const {current} = pagination;
    this.setState({
      groupsTableCurrentPage: current,
      groupsTableFilter: filter,
      groupsTableSorter: sorter
    });
  };

  @observable _findUsers;

  fetchUsers = async () => {
    let request;
    if (this.state.userSearchText) {
      request = this._findUsers = new UserFind(this.state.userSearchText);
    } else {
      this._findUsers = null;
      // todo: Uncomment when server-side sorting & pagination would be implemented
      // request = new Users();
    }
    if (request) {
      // todo: apply sorting & pagination info to request payload
      // .....
      await request.fetch();
    }
  };

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return (this.props.dataStorages.value || [])
        .filter(d => roleModel.writeAllowed(d)).map(d => d);
    }
    return [];
  }

  reload = async () => {
    if (this._findUsers) {
      this._findUsers.fetch();
    }
    this.props.users.fetch();
    this.props.roles.fetch();
  };

  mapUserRolesAndGroups = (user) => {
    const splitRoleName = (name) => {
      if (name && name.toLowerCase().indexOf('role_') === 0) {
        return name.substring('role_'.length);
      }
      return name;
    };
    return {
      ...user,
      userRoles: (user.roles || []).filter(r => r.predefined).map(r => {
        return {
          ...r,
          displayName: r.name,
          isADGroup: false
        };
      }),
      userGroups: [
        ...(user.roles || [])
          .filter(r => !r.predefined && r.name)
          .map(r => {
            return {
              ...r,
              displayName: splitRoleName(r.name),
              isADGroup: false
            };
          }),
        ...(user.groups || []).map(group => {
          return {
            displayName: group,
            isADGroup: true
          };
        })
      ]
    };
  };

  @computed
  get users () {
    if (this.state.userSearchText &&
      this.state.userSearchText.trim().length &&
      this._findUsers &&
      this._findUsers.loaded) {
      return (this._findUsers.value || []).map(this.mapUserRolesAndGroups).sort(this.alphabeticNameSorter);
    } else if (this.props.users.loaded) {
      return (this.props.users.value || []).map(this.mapUserRolesAndGroups).sort(this.alphabeticNameSorter);
    }
    return [];
  }

  @computed
  get usersPending () {
    return this.props.users.pending || (!!this._findUsers && this._findUsers.pending);
  }

  onUserSearchChanged = (e) => {
    let userSearchText = e.target.value.trim();
    if (userSearchText.length === 0) {
      userSearchText = null;
    }
    if (this.state.userSearchText !== userSearchText) {
      this.setState({
        usersTableCurrentPage: 1,
        userSearchText: userSearchText
      });
    }
  };

  onRoleSearchChanged = (e) => {
    let rolesSearchText = e.target.value.trim();
    if (rolesSearchText.length === 0) {
      rolesSearchText = null;
    }
    if (this.state.rolesSearchText !== rolesSearchText) {
      this.setState({
        rolesTableCurrentPage: 1,
        rolesSearchText: rolesSearchText
      });
    }
  };

  onGroupSearchChanged = (e) => {
    let groupsSearchText = e.target.value.trim();
    if (groupsSearchText.length === 0) {
      groupsSearchText = null;
    }
    if (this.state.groupsSearchText !== groupsSearchText) {
      this.setState({
        groupsTableCurrentPage: 1,
        groupsSearchText: groupsSearchText
      });
    }
  };

  alphabeticNameSorter = (a, b) => {
    return this.alphabeticSorter(a.userName, b.userName);
  };

  alphabeticRoleNameSorter = (a, b) => {
    return this.alphabeticSorter(a.name, b.name);
  };

  alphabeticSorter = (a, b) => {
    if (a === b) {
      return 0;
    } else if (a > b) {
      return 1;
    } else {
      return -1;
    }
  };

  openEditUserRolesDialog = (user) => {
    this.setState({editableUser: user});
  };

  closeEditUserRolesDialog = () => {
    this.setState({editableUser: null}, this.reload);
  };

  openEditRoleDialog = (role) => {
    this.setState({editableRole: role});
  };

  closeEditRoleDialog = () => {
    this.setState({editableRole: null}, this.reload);
  };

  openEditGroupDialog = (role) => {
    this.setState({editableGroup: role});
  };

  closeEditGroupDialog = () => {
    this.setState({editableGroup: null}, this.reload);
  };

  renderUsersTable = () => {
    const renderTagsList = (tags, tagClassName, maxTagItems) => {
      const tagRenderer = (tag, index) =>
        <span
          key={index}
          className={`${tagClassName} tag-${tag.displayName.replace(new RegExp(' ', 'g'), '-')} ${tag.isADGroup ? styles.ad : ''}`}>
          {tag.displayName}
        </span>;
      if (tags.length - 1 > maxTagItems) {
        return (
          <Row type="flex">
            {[
              ...tags.slice(0, maxTagItems - 1).map(tagRenderer),
              <Dropdown
                key="more"
                overlay={
                  <Card
                    className="all-tags-container"
                    bodyStyle={{
                      padding: 5,
                      overflowY: 'auto',
                      maxHeight: '30vh'
                    }}>
                    {tags.map((tag, index) =>
                      <Row
                        className={`tag-${index} ${tag.isADGroup ? styles.ad : ''}`}
                        key={index}
                        type="flex">{tag.displayName}</Row>)
                    }
                  </Card>
                }>
                <a id="more-info-link" className={styles.moreInfoLabel}>+{tags.length - maxTagItems + 1} more</a>
              </Dropdown>
            ]}
          </Row>
        );
      } else {
        return <Row type="flex">{tags.map(tagRenderer)}</Row>;
      }
    };
    const columns = [
      {
        dataIndex: 'userName',
        key: 'name',
        title: 'Name',
        sorter: this.alphabeticNameSorter,
        className: styles.userNameColumn,
        render: (name, user) => {
          if (user.attributes) {
            const getAttributesValues = () => {
              const values = [];
              for (let key in user.attributes) {
                if (user.attributes.hasOwnProperty(key)) {
                  values.push(user.attributes[key]);
                }
              }
              return values;
            };
            const attributesString = getAttributesValues().join(', ');
            return (
              <Row type="flex" style={{flexDirection: 'column'}}>
                <Row>{name}</Row>
                <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
              </Row>
            );
          } else {
            return name;
          }
        }
      },
      {
        dataIndex: 'userGroups',
        key: 'groups',
        title: 'Groups',
        render: (groups) => renderTagsList((groups || []), styles.userGroup, 10),
        className: styles.groupsColumn
      },
      {
        dataIndex: 'userRoles',
        key: 'roles',
        title: 'Roles',
        render: (roles) => renderTagsList((roles || []), styles.userRole, 10),
        className: styles.rolesColumn
      },
      {
        key: 'actions',
        render: (user) => {
          return (
            <Row type="flex" justify="end">
              <Button id="edit-user-button" size="small" onClick={() => this.openEditUserRolesDialog(user)}>
                <Icon type="edit" />
              </Button>
            </Row>
          );
        }
      }
    ];
    return (
      <Table
        className={styles.table}
        rowKey="id"
        loading={this.usersPending}
        columns={columns}
        dataSource={this.users}
        onChange={this.handleUserTableChange}
        rowClassName={user => `user-${user.id}`}
        pagination={{total: this.users.length, PAGE_SIZE, current: this.state.usersTableCurrentPage}}
        size="small" />
    );
  };

  deleteRoleConfirm = (role) => {
    const deleteRole = async () => {
      const hide = message.loading('Removing role...', 0);
      const request = new RoleRemove(role.id);
      await request.send({});
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        return this.reload();
      }
    };
    const splitRoleName = (name) => {
      if (name && name.toLowerCase().indexOf('role_') === 0) {
        return name.substring('role_'.length);
      }
      return name;
    };
    Modal.confirm({
      title: `Are you sure you want to delete ${role.predefined ? 'role' : 'group'} '${role.predefined ? role.name : splitRoleName(role.name)}'?`,
      content: 'This operation cannot be undone.',
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        return deleteRole();
      }
    });
  };

  renderRolesTable = () => {
    if (!this.props.roles.loaded) {
      return <LoadingView />;
    }
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Role',
        sorter: this.alphabeticRoleNameSorter
      },
      {
        key: 'actions',
        render: (role) => {
          return (
            <Row className={styles.roleActions} type="flex" justify="end">
              <Button size="small" onClick={() => this.openEditRoleDialog(role)}>
                <Icon type="edit" />
              </Button>
            </Row>
          );
        }
      }
    ];

    const roles = (this.props.roles.value || [])
      .map(r => r)
      .filter(r => r.predefined)
      .filter(r => !this.state.rolesSearchText ||
      !this.state.rolesSearchText.length ||
      r.name.toLowerCase().indexOf(this.state.rolesSearchText.toLowerCase()) >= 0);

    return (
      <Table
        className={styles.table}
        rowKey="id"
        loading={this.props.roles.pending}
        columns={columns}
        dataSource={roles}
        onChange={this.handleRolesTableChange}
        pagination={{total: roles.length, PAGE_SIZE, current: this.state.rolesTableCurrentPage}}
        size="small" />
    );
  };

  renderGroupsTable = () => {
    if (!this.props.roles.loaded) {
      return <LoadingView />;
    }
    const columns = [
      {
        dataIndex: 'displayName',
        key: 'name',
        title: 'Group',
        sorter: this.alphabeticRoleNameSorter
      },
      {
        key: 'actions',
        render: (role) => {
          return (
            <Row className={styles.roleActions} type="flex" justify="end">
              <Button size="small" onClick={() => this.openEditGroupDialog(role)}>
                <Icon type="edit" />
              </Button>
              <Button size="small" type="danger" onClick={() => this.deleteRoleConfirm(role)}>
                <Icon type="delete" />
              </Button>
            </Row>
          );
        }
      }
    ];

    const splitRoleName = (name) => {
      if (name && name.toLowerCase().indexOf('role_') === 0) {
        return name.substring('role_'.length);
      }
      return name;
    };

    const roles = (this.props.roles.value || [])
      .map(r => {
        return {
          ...r,
          displayName: splitRoleName(r.name)
        };
      })
      .filter(r => !r.predefined)
      .filter(r => !this.state.groupsSearchText ||
      !this.state.groupsSearchText.length ||
      r.displayName.toLowerCase().indexOf(this.state.groupsSearchText.toLowerCase()) >= 0);

    return (
      <Table
        className={styles.table}
        rowKey="id"
        loading={this.props.roles.pending}
        columns={columns}
        dataSource={roles}
        onChange={this.handleGroupsTableChange}
        pagination={{total: roles.length, PAGE_SIZE, current: this.state.groupsTableCurrentPage}}
        size="small" />
    );
  };

  openCreateUserDialog = () => {
    this.setState({
      createUserDialogVisible: true
    });
  };

  closeCreateUserDialog = () => {
    this.setState({
      createUserDialogVisible: false
    });
  };

  createUser = async ({userName, defaultStorageId, roleIds}) => {
    const hide = message.loading('Creating user...', 0);
    const request = new UserCreate();
    await request.send({userName, defaultStorageId, roleIds: roleIds.map(r => +r)});
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.setState({
        createUserDialogVisible: false
      }, this.reload);
    }
  };

  deleteUser = async (id) => {
    const hide = message.loading('Removing user...', 0);
    const request = new UserDelete(id);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeEditUserRolesDialog();
    }
  };

  openCreateGroupDialog = () => {
    this.setState({
      createGroupDialogVisible: true
    });
  };

  closeCreateGroupDialog = () => {
    this.setState({
      createGroupDialogVisible: false,
      createGroupDefaultDataStorage: null,
      createGroupName: null
    });
  };

  createGroup = async () => {
    const hide = message.loading('Creating group...', 0);
    const request = new RoleCreate(
      this.state.createGroupName,
      this.state.createGroupDefault,
      this.state.createGroupDefaultDataStorage
    );
    await request.send({});
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.setState({
        createGroupDialogVisible: false,
        createGroupName: null,
        createGroupDefaultDataStorage: null,
        createGroupDefault: false
      }, this.reload);
    }
  };

  createGroupNameChanged = (e) => {
    this.setState({
      createGroupName: e.target.value
    });
  };

  createGroupDefaultChanged = (e) => {
    this.setState({
      createGroupDefault: e.target.checked
    });
  };

  createGroupDefaultDataStorageChanged = (defaultStorageId) => {
    this.setState({
      createGroupDefaultDataStorage: defaultStorageId
    });
  };

  render () {
    return (
      <Tabs className="user-management-tabs" style={{width: '100%'}} type="card">
        <Tabs.TabPane tab="Users" key="users">
          <Row type="flex" style={{marginBottom: 10}}>
            <Input.Search
              id="search-users-input"
              size="small"
              style={{flex: 1}}
              value={this.state.userSearchText}
              onPressEnter={this.fetchUsers}
              onChange={this.onUserSearchChanged} />
            <Button size="small" style={{marginLeft: 5}} onClick={this.openCreateUserDialog}>
              <Icon type="plus" />Create user
            </Button>
          </Row>
          {this.renderUsersTable()}
          <EditUserRolesDialog
            visible={!!this.state.editableUser}
            onUserDelete={this.deleteUser}
            onClose={this.closeEditUserRolesDialog}
            user={this.state.editableUser} />
          <CreateUserForm
            roles={this.props.roles.loaded ? (this.props.roles.value || []).map(r => r) : []}
            pending={this.state.operationInProgress}
            onCancel={this.closeCreateUserDialog}
            onSubmit={this.operationWrapper(this.createUser)}
            visible={this.state.createUserDialogVisible} />
        </Tabs.TabPane>
        <Tabs.TabPane tab="Groups" key="groups">
          <Row type="flex" style={{marginBottom: 10}}>
            <div style={{flex: 1}}>
              <Input.Search
                id="search-groups-input"
                size="small"
                style={{width: '100%'}}
                value={this.state.groupsSearchText}
                onChange={this.onGroupSearchChanged} />
            </div>
            <div style={{paddingLeft: 10}}>
              <Button size="small" type="primary" onClick={this.openCreateGroupDialog}>
                <Icon type="plus" /> Create group
              </Button>
            </div>
          </Row>
          {this.renderGroupsTable()}
          <EditRoleDialog
            visible={!!this.state.editableGroup}
            onClose={this.closeEditGroupDialog}
            role={this.state.editableGroup} />
          <Modal
            title="Create group"
            footer={
              <Row type="flex" justify="space-between">
                <Button onClick={this.closeCreateGroupDialog}>
                  Cancel
                </Button>
                <Button
                  disabled={!this.state.createGroupName}
                  type="primary"
                  onClick={this.createGroup}>
                  Create
                </Button>
              </Row>
            }
            onCancel={this.closeCreateGroupDialog}
            visible={this.state.createGroupDialogVisible}>
            <Row type="flex" align="middle">
              <div style={{flex: 1}}>
                <Input
                  placeholder="Enter group name"
                  value={this.state.createGroupName}
                  onChange={this.createGroupNameChanged} />
              </div>
              <div style={{paddingLeft: 10}}>
                <Checkbox
                  onChange={this.createGroupDefaultChanged}
                  checked={this.state.createGroupDefault}>
                  Default
                </Checkbox>
              </div>
            </Row>
            <Row style={{marginTop: 15, paddingLeft: 2, marginBottom: 2}}>
              Default data storage:
            </Row>
            <Row type="flex" style={{marginBottom: 10}} align="middle">
              <Select
                allowClear
                showSearch
                disabled={this.state.operationInProgress}
                style={{flex: 1}}
                value={this.state.createGroupDefaultDataStorage}
                onChange={this.createGroupDefaultDataStorageChanged}
                filterOption={(input, option) =>
                  option.props.name.toLowerCase().indexOf(input.toLowerCase()) >= 0 ||
                  option.props.pathMask.toLowerCase().indexOf(input.toLowerCase()) >= 0
                }>
                {
                  this.dataStorages.map(d => {
                    return (
                      <Select.Option
                        key={d.id} value={`${d.id}`} name={d.name} pathMask={d.pathMask}>
                        <b>{d.name}</b> ({d.pathMask})
                      </Select.Option>
                    );
                  })
                }
              </Select>
            </Row>
          </Modal>
        </Tabs.TabPane>
        <Tabs.TabPane tab="Roles" key="roles">
          <Row type="flex" style={{marginBottom: 10}}>
            <Input.Search
              id="search-roles-input"
              size="small"
              style={{width: '100%'}}
              value={this.state.rolesSearchText}
              onChange={this.onRoleSearchChanged} />
          </Row>
          {this.renderRolesTable()}
          <EditRoleDialog
            visible={!!this.state.editableRole}
            onClose={this.closeEditRoleDialog}
            role={this.state.editableRole} />
        </Tabs.TabPane>
      </Tabs>
    );
  }

  componentDidMount () {
    this.props.onInitialized && this.props.onInitialized(this);
  }
}
