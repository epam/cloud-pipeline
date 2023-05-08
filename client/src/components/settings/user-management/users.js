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
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {
  Table,
  Row,
  Input,
  Dropdown,
  Card,
  Icon,
  Button,
  message,
  Alert,
  Select,
  Tooltip
} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import Roles from '../../../models/user/Roles';
import UserCreate from '../../../models/user/UserCreate';
import UserDelete from '../../../models/user/UserDelete';
import EditUserRolesDialog from '../forms/EditUserRolesDialog';
import ExportUserForm, {doExport, DefaultValues} from '../forms/ExportUserForm';
import CreateUserForm from '../forms/CreateUserForm';
import ImportUsersButton from './../components/import-users';
import roleModel from '../../../utils/roleModel';
import {alphabeticSorter} from './utilities';
import styles from '../UserManagementForm.css';
import UserStatus from './user-status-indicator';
import displayDate from '../../../utils/displayDate';
import {QuotasDisclaimerComponent} from './quota-info';
import MarkedToBeBlockedInfo from './marked-to-be-blocked-info';

const PAGE_SIZE = 20;

function usersFilter (criteria) {
  const search = criteria ? criteria.toLowerCase() : criteria;
  return function filter (user) {
    return !search ||
      (user.userName || '').toLowerCase().startsWith(search) ||
      (user.email || '').toLowerCase().startsWith(search) ||
      (Object.values(user.attributes || {}).some(o => o.toLowerCase().startsWith(search)));
  };
}

const USERS_FILTERS = {
  all: 'all',
  blocked: 'blocked',
  markedToBeBlocked: 'marked-to-be-blocked',
  online: 'online',
  quota: 'quota'
};

@roleModel.authenticationInfo
@inject('dataStorages', 'usersWithActivity', 'userMetadataKeys', 'users')
@inject(({usersWithActivity, authenticatedUserInfo, userMetadataKeys, users}) => ({
  users: usersWithActivity,
  usersStore: users,
  authenticatedUserInfo,
  roles: new Roles(),
  userMetadataKeys
}))
@observer
export default class UsersManagement extends React.Component {
  state = {
    filter: undefined,
    userSearchText: undefined,
    usersTableCurrentPage: 1,
    editableUser: undefined,
    exportUserDialogVisible: false,
    createUserDialogVisible: false,
    groupsSearchText: undefined,
    operationInProgress: false,
    userDataToExport: [],
    metadataKeys: [],
    filterUsers: USERS_FILTERS.all
  };

  get isAdmin () {
    const {authenticatedUserInfo} = this.props;
    return authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value.admin
      : false;
  };

  get isReader () {
    return roleModel.hasRole('ROLE_USER_READER')(this);
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

  handleUserTableChange = (pagination) => {
    const {current} = pagination;
    this.setState({
      usersTableCurrentPage: current
    });
  };

  handleExportUsersMenu = ({key}) => {
    key === 'custom' && this.openExportUserDialog();
    key === 'default' && doExport();
  };

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return (this.props.dataStorages.value || [])
        .filter(d => roleModel.writeAllowed(d)).map(d => d);
    }
    return [];
  }

  onImportDone = () => {
    return this.reload();
  };

  reload = () => {
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
    const {users} = this.props;
    if (users && users.loaded) {
      return (users.value || [])
        .map(this.mapUserRolesAndGroups)
        .sort(this.alphabeticNameSorter);
    }
    return [];
  }

  @computed
  get usersPending () {
    const {users} = this.props;
    return users.pending;
  }

  get filteredUsers () {
    const {
      userSearchText,
      filterUsers
    } = this.state;
    return this.users
      .filter(usersFilter(userSearchText))
      .filter(user => {
        switch (filterUsers) {
          case USERS_FILTERS.all: {
            return true;
          }
          case USERS_FILTERS.blocked: {
            return user.blocked;
          }
          case USERS_FILTERS.markedToBeBlocked: {
            return !!user.externalBlockDate;
          }
          case USERS_FILTERS.online: {
            return user.online;
          }
          case USERS_FILTERS.quota: {
            return (user.activeQuotas || []).length > 0;
          }
        }
      });
  }

  onUserSearchChanged = (e) => {
    let userSearchText = e.target.value;
    this.setState({
      userSearchText: userSearchText,
      usersTableCurrentPage: 1
    });
  };

  alphabeticNameSorter = (a, b) => {
    return alphabeticSorter(a.userName, b.userName);
  };

  openEditUserRolesDialog = (user) => {
    this.setState({editableUser: user});
  };

  closeEditUserRolesDialog = () => {
    this.setState({editableUser: undefined}, this.reload);
  };

  onChangeUsersFilters = (e) => {
    this.setState({
      filterUsers: e,
      usersTableCurrentPage: 1
    });
  };

  renderUsersTableControls = () => {
    const exportUserMenu = (
      <Menu
        onClick={this.handleExportUsersMenu}
        selectedKeys={[]}
        style={{cursor: 'pointer'}}
      >
        <MenuItem key="default">
          <Icon type="download" style={{marginRight: 10}} />
          Default configuration
        </MenuItem>
        <MenuItem key="custom">
          <Icon type="bars" style={{marginRight: 10}} />
          Custom configuration
        </MenuItem>
      </Menu>
    );
    return (
      <Row type="flex" style={{marginBottom: 10}}>
        <Input.Search
          id="search-users-input"
          placeholder="Search users"
          style={{flex: 1}}
          value={this.state.userSearchText}
          onChange={this.onUserSearchChanged}
        />
        <Select
          value={this.state.filterUsers}
          style={{width: 175, marginLeft: 5}}
          onChange={this.onChangeUsersFilters}
          dropdownMatchSelectWidth={false}
        >
          <Select.Option
            key={USERS_FILTERS.all}
            value={USERS_FILTERS.all}
          >
            All
          </Select.Option>
          <Select.Option
            key={USERS_FILTERS.blocked}
            value={USERS_FILTERS.blocked}
          >
            Blocked
          </Select.Option>
          <Select.Option
            key={USERS_FILTERS.markedToBeBlocked}
            value={USERS_FILTERS.markedToBeBlocked}
          >
            Marked to be blocked
          </Select.Option>
          { this.isAdmin && (
            <Select.Option
              key={USERS_FILTERS.online}
              value={USERS_FILTERS.online}
            >
              Online
            </Select.Option>)
          }
          { this.isAdmin && (
            <Select.Option
              key={USERS_FILTERS.quota}
              value={USERS_FILTERS.quota}
            >
              Exceeded quotas
            </Select.Option>)
          }
        </Select>
        {
          this.isAdmin && (
            <Button
              style={{marginLeft: 5}}
              onClick={this.openCreateUserDialog}
            >
              <Icon type="plus" />Create user
            </Button>
          )
        }
        {
          this.isAdmin && (
            <ImportUsersButton
              style={{marginLeft: 5}}
              onImportDone={this.onImportDone}
            />
          )
        }
        {
          (this.isReader || this.isAdmin) && (
            <Dropdown.Button
              style={{marginLeft: 5}}
              onClick={() => doExport()}
              overlay={exportUserMenu}
              icon={<Icon type="download" />}
            >
              Export users
            </Dropdown.Button>
          )
        }
      </Row>
    );
  }

  renderUsersTable = () => {
    const renderTagsList = (tags, tagClassName, maxTagItems) => {
      const tagRenderer = (tag, index) =>
        <span
          key={index}
          className={
            classNames(
              tagClassName,
              `tag-${tag.displayName.replace(new RegExp(' ', 'g'), '-')}`,
              'cp-tag',
              {
                'disabled': tag.isADGroup
              }
            )
          }
        >
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
                    {tags.map((tag, index) => (
                      <Row
                        className={
                          classNames(
                            `tag-${index}`,
                            {'cp-text-not-important': tag.isADGroup}
                          )
                        }
                        key={index}
                        type="flex">
                        {tag.displayName}
                      </Row>))
                    }
                  </Card>
                }>
                <a
                  id="more-info-link"
                  className={styles.moreInfoLabel}
                >
                  +{tags.length - maxTagItems + 1} more
                </a>
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
          let blockedSpan;
          let blockInfo;
          const offlineInfo = (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'flex-start',
                justifyContent: 'flex-start'
              }}
            >
              <p>Offline</p>
              {
                user.lastLoginDate &&
                (
                  <p>
                    Last visited: {displayDate(user.lastLoginDate, 'D MMMM YYYY, HH:mm')}
                  </p>
                )
              }
            </div>);
          const onlineInfo = (
            <div>
              <p>Online</p>
            </div>);

          if (user.blocked) {
            blockedSpan = (
              <span
                style={{fontStyle: 'italic', marginLeft: 5}}
              >
                - blocked
              </span>
            );
          }
          if (user.externalBlockDate) {
            blockInfo = (
              <MarkedToBeBlockedInfo
                className="cp-error"
                style={{
                  cursor: 'pointer',
                  fontSize: 'smaller'
                }}
                externalBlockDate={user.externalBlockDate}
              />
            );
          }
          const userStatus = this.isAdmin
            ? (
              <Tooltip
                placement="left"
                title={user.online ? onlineInfo : offlineInfo}
                trigger="hover"
              >
                <div style={{marginRight: 5}}>
                  <UserStatus online={user.online} />
                </div>
              </Tooltip>
            )
            : undefined;
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
                <Row>
                  <span className={styles.lineBreak}>
                    {userStatus}
                    <span>
                      {name}
                      {blockedSpan}
                    </span>
                  </span>
                </Row>
                <Row>
                  <span
                    style={{fontSize: 'smaller'}}
                    className={styles.lineBreak}
                  >
                    {attributesString}
                  </span>
                  <QuotasDisclaimerComponent
                    style={{fontSize: 'smaller', cursor: 'pointer'}}
                    quotas={user.activeQuotas || []}
                  />
                </Row>
                {
                  blockInfo && (
                    <Row>
                      {blockInfo}
                    </Row>
                  )
                }
              </Row>
            );
          }
          return (
            <Row type="flex" style={{flexDirection: 'column'}}>
              <Row>
                <span className={styles.lineBreak}>
                  {userStatus}
                  <span className={styles.userName}>
                    {name}
                    {blockedSpan}
                  </span>
                </span>
              </Row>
              {
                blockInfo && (
                  <Row>
                    {blockInfo}
                  </Row>
                )
              }
            </Row>
          );
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
      this.isAdmin
        ? {
          key: 'actions',
          render: (user) => {
            return (
              <Row type="flex" justify="end">
                <Button
                  id="edit-user-button"
                  size="small"
                  onClick={() => this.openEditUserRolesDialog(user)}
                >
                  <Icon type="edit" />
                </Button>
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
        loading={this.usersPending}
        columns={columns}
        dataSource={this.filteredUsers}
        onChange={this.handleUserTableChange}
        rowClassName={user => `user-${user.id}`}
        onRowClick={(user) => this.openEditUserRolesDialog(user)}
        pagination={{
          total: this.filteredUsers.length,
          PAGE_SIZE,
          current: this.state.usersTableCurrentPage
        }}
        size="small"
      />
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

  openExportUserDialog = () => {
    const hide = message.loading('Fetching attributes list...', 0);
    this.props.userMetadataKeys.fetch()
      .then(() => {
        hide();
        this.setState({
          exportUserDialogVisible: true,
          userDataToExport: DefaultValues,
          metadataKeys: []
        });
      });
  };

  closeExportUserDialog = () => {
    this.setState({
      exportUserDialogVisible: false,
      userDataToExport: [],
      metadataKeys: []
    });
  };

  handleExportUsersChange = (checkedValues, metadataKeys) => {
    this.setState({userDataToExport: checkedValues, metadataKeys});
  };

  createUser = async ({userName, defaultStorageId, roleIds}) => {
    const hide = message.loading('Creating user...', 0);
    const request = new UserCreate();
    await request.send({userName, defaultStorageId, roleIds: roleIds.map(r => +r)});
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await this.props.dataStorages.fetch();
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

  render () {
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return null;
    }
    if (!this.isReader && !this.isAdmin) {
      return (
        <Alert type="error" message="Access is denied" />
      );
    }
    const {
      exportUserDialogVisible,
      userDataToExport,
      metadataKeys
    } = this.state;
    return (
      <div className={styles.container}>
        {this.renderUsersTableControls()}
        {this.renderUsersTable()}
        <EditUserRolesDialog
          visible={!!this.state.editableUser}
          onUserDelete={this.deleteUser}
          onClose={this.closeEditUserRolesDialog}
          user={this.state.editableUser}
          readOnly={!this.isAdmin}
        />
        <ExportUserForm
          visible={exportUserDialogVisible}
          values={userDataToExport}
          selectedMetadataKeys={metadataKeys}
          onChange={this.handleExportUsersChange}
          onCancel={this.closeExportUserDialog}
          onSubmit={doExport}
          metadataKeys={
            this.props.userMetadataKeys.loaded
              ? (this.props.userMetadataKeys.value || []).map(o => o)
              : []
          }
        />
        <CreateUserForm
          roles={this.props.roles.loaded ? (this.props.roles.value || []).map(r => r) : []}
          pending={this.state.operationInProgress}
          onCancel={this.closeCreateUserDialog}
          onSubmit={this.operationWrapper(this.createUser)}
          visible={this.state.createUserDialogVisible}
        />
      </div>
    );
  }

  componentDidMount () {
    this.props.users.fetch();
  }

  componentWillUnmount () {
    this.props.usersStore.fetch();
  }
}
