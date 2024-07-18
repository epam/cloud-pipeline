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
import {
  Alert,
  AutoComplete,
  Button,
  Checkbox,
  Col,
  Icon,
  Modal,
  Popover,
  Row,
  Table,
  Select
} from 'antd';
import {isObservableArray, observable, computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import GrantGet from '../../models/grant/GrantGet';
import GetAllPermissions from '../../models/grant/GetAllPermissions';
import UserFind from '../../models/user/UserFind';
import GroupFind from '../../models/user/GroupFind';
import Roles from '../../models/user/Roles';
import styles from './PermissionsForm.css';
import roleModel from '../../utils/roleModel';
import UserName from '../special/UserName';
import compareSubObjects from './utilities/compare-sub-objects';
import {
  applyPermissionChanges,
  filterRemovePermissionBySid,
  findPermissionByPermission,
  findPermissionBySidFn, getPermissionChanges,
  getPermissionsHash, permissionSidsEqual
} from './utilities/permissions';

function plural (count, noun) {
  return `${noun}${count > 1 ? 's' : ''}`;
}

const MAX_SUB_OBJECTS_WARNINGS_TO_SHOW = 5;

const ALL_ALLOWED_MASK = roleModel.buildPermissionsMask(1, 1, 1, 1, 1, 1);

function findMaskForSubject (config, subject, isPrincipal, defaultMask = 0) {
  if (typeof config === 'number') {
    return config;
  }
  if (config && (Array.isArray(config) || isObservableArray(config))) {
    const all = config
      .find((aMask) => /^all$/i.test(aMask.role));
    const rule = config
      .find((aMask) => !isPrincipal &&
        (subject || '').toLowerCase() === (aMask.role || '').toLowerCase());
    if (rule) {
      return rule.mask;
    }
    if (all) {
      return all.mask;
    }
  }
  return defaultMask;
}

@inject('usersInfo')
@inject(({routing, authenticatedUserInfo}, params) => ({
  authenticatedUserInfo,
  grant: new GrantGet(params.objectIdentifier, params.objectType),
  roles: new Roles()
}))
@observer
export default class PermissionsForm extends React.Component {
  state = {
    findUserVisible: false,
    findGroupVisible: false,
    selectedPermission: undefined,
    groupSearchString: undefined,
    selectedUser: undefined,
    owner: undefined,
    ownerInput: undefined,
    fetching: false,
    fetchedUsers: [],
    roleName: undefined,
    subObjectsPermissions: [],
    searchUserTouched: false,
    pending: false,
    error: undefined,
    permissions: [],
    originalPermissions: [],
    originalOwner: undefined,
    entity: undefined
  };

  @observable
  groupFind;

  static propTypes = {
    objectType: PropTypes.string,
    objectIdentifier: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    readonly: PropTypes.bool,
    defaultMask: PropTypes.oneOfType([
      PropTypes.number,
      PropTypes.arrayOf(PropTypes.shape({
        mask: PropTypes.number,
        role: PropTypes.string
      }))
    ]),
    enabledMask: PropTypes.oneOfType([
      PropTypes.number,
      PropTypes.arrayOf(PropTypes.shape({
        mask: PropTypes.number,
        role: PropTypes.string
      }))
    ]),
    subObjectsPermissionsMaskToCheck: PropTypes.number,
    subObjectsToCheck: PropTypes.arrayOf(PropTypes.shape({
      entityId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      entityClass: PropTypes.string,
      name: PropTypes.node,
      description: PropTypes.node
    })),
    subObjectsPermissionsErrorTitle: PropTypes.node,
    showOwner: PropTypes.bool
  };

  static defaultProps = {
    enabledMask: ALL_ALLOWED_MASK,
    subObjectsPermissionsMaskToCheck: 0,
    showOwner: true
  }

  @computed
  get allUsers () {
    if (this.props.usersInfo.loaded) {
      return this.props.usersInfo.value || [];
    }
    return [];
  }

  get permissionsChanged () {
    const {
      originalOwner,
      owner,
      permissions,
      originalPermissions
    } = this.state;
    return getPermissionsHash(permissions) !== getPermissionsHash(originalPermissions) ||
      owner !== originalOwner;
  }

  findUser = (value) => {
    this._ownerFetchId = {};
    const fetchId = this._ownerFetchId;
    this.setState({
      ownerInput: value,
      fetching: true,
      selectedUser: undefined
    }, async () => {
      const request = new UserFind(value);
      await request.fetch();
      if (fetchId === this._ownerFetchId) {
        let fetchedUsers = [];
        if (!request.error) {
          fetchedUsers = (request.value || []).map(u => u);
        }
        this.setState({
          fetching: false,
          fetchedUsers
        });
      }
    });
  };

  onUserSelect = (key) => {
    const [user] = this.state.fetchedUsers.filter(u => `${u.id}` === `${key}`);
    if (user) {
      this.setState({
        ownerInput: user.userName,
        owner: user.userName
      });
    }
  };

  onUserFindInputChanged = (value) => {
    this.setState({selectedUser: value});
  };

  onGroupFindInputChanged = (value) => {
    this.selectedGroup = value;
    if (value && value.length) {
      this.groupFind = new GroupFind(value);
      this.groupFind.fetch();
    } else {
      this.groupFind = undefined;
    }
    this.setState({groupSearchString: value});
  };

  renderGroupAndUsersActions = () => {
    return (
      <span className={styles.actions}>
        <Button disabled={this.props.readonly} size="small" onClick={this.openFindUserDialog}>
          <Icon type="user-add" />
        </Button>
        <Button disabled={this.props.readonly} size="small" onClick={this.openFindGroupDialog}>
          <Icon type="usergroup-add" />
        </Button>
      </span>
    );
  };

  splitRoleName = (name) => {
    if (name && name.toLowerCase().indexOf('role_') === 0) {
      return name.substring('role_'.length);
    }
    return name;
  };

  findGroupDataSource = () => {
    const {permissions = [], groupSearchString} = this.state;
    const {roles: rolesRequest} = this.props;
    const existingGroups = new Set(
      permissions
        .filter(p => p.sid && !p.sid.principal)
        .map(p => p.sid.name)
    );
    const roles = (rolesRequest.loaded && groupSearchString) ? (
      (rolesRequest.value || [])
        .filter(r => r.name.toLowerCase().indexOf(groupSearchString.toLowerCase()) >= 0)
        .filter(r => !existingGroups.has(r.name))
        .map(r => r.predefined ? r.name : this.splitRoleName(r.name))
    ) : [];
    if (this.groupFind && !this.groupFind.pending && !this.groupFind.error) {
      return [...roles, ...(this.groupFind.value || []).map(g => g)];
    }
    return [...roles];
  };

  selectedGroup = undefined;

  openFindUserDialog = () => {
    this.setState({
      findUserVisible: true
    });
  };

  closeFindUserDialog = () => {
    this.setState({
      selectedUser: undefined,
      findUserVisible: false
    });
  };

  getDefaultMaskForSubject = (subject, isPrincipal) => {
    const {
      defaultMask = []
    } = this.props;
    return findMaskForSubject(defaultMask, subject, isPrincipal, 0);
  };

  getEnabledMaskForSubject = (subject, isPrincipal) => {
    const {
      enabledMask = []
    } = this.props;
    return findMaskForSubject(enabledMask, subject, isPrincipal, ALL_ALLOWED_MASK);
  };

  onSelectUser = () => {
    this.grantPermission(
      this.state.selectedUser,
      true,
      this.getDefaultMaskForSubject(this.state.selectedUser, true)
    );
    this.closeFindUserDialog();
  };

  openFindGroupDialog = () => {
    this.selectedGroup = undefined;
    this.setState({findGroupVisible: true, groupSearchString: undefined});
  };

  closeFindGroupDialog = () => {
    this.setState({findGroupVisible: false, groupSearchString: undefined});
  };

  onSelectGroup = async () => {
    const [role] = (this.props.roles.loaded ? this.props.roles.value || [] : [])
      .filter(r => !r.predefined && this.splitRoleName(r.name) === this.selectedGroup);
    const roleName = role ? role.name : this.selectedGroup;
    this.grantPermission(
      roleName,
      false,
      this.getDefaultMaskForSubject(roleName, false)
    );
    this.closeFindGroupDialog();
  };

  grantPermission = (name, principal, mask) => {
    const {
      permissions = []
    } = this.state;
    const sid = {name, principal};
    const newPermissions = permissions
      .filter(filterRemovePermissionBySid(sid))
      .concat({sid, mask});
    const selectedPermission = newPermissions
      .find(findPermissionBySidFn(sid));
    this.setState({
      permissions: newPermissions,
      selectedPermission
    });
  };

  removeUserOrGroupClicked = (item) => (event) => {
    event.stopPropagation();
    const {
      permissions = [],
      selectedPermission
    } = this.state;
    const {sid} = item;
    const newPermissions = permissions
      .filter(filterRemovePermissionBySid(sid));
    const selectFirstPermission = !selectedPermission ||
      permissionSidsEqual(selectedPermission.sid, sid);
    this.setState({
      permissions: newPermissions,
      selectedPermission: selectFirstPermission ? undefined : selectedPermission
    }, () => {
      if (selectFirstPermission) {
        this.selectFirstPermission();
      }
    });
  };

  onAllowDenyValueChanged = (permissionMask, allowDenyMask, allowRead = false) => async (event) => {
    const mask = (1 | 1 << 1 | 1 << 2 | 1 << 3 | 1 << 4 | 1 << 5) ^ permissionMask;
    let newValue = 0;
    if (event.target.checked) {
      newValue = allowDenyMask;
    }
    const {selectedPermission} = this.state;
    if (selectedPermission) {
      let {mask: currentMask, sid} = selectedPermission;
      currentMask = (currentMask & mask) | newValue;
      if (allowRead && event.target.checked) {
        currentMask = (currentMask & (1 << 2 | 1 << 3 | 1 << 4 | 1 << 5)) | 1;
      }
      this.grantPermission(sid.name, sid.principal, currentMask);
    }
  };

  renderSubObjectsWarnings = () => {
    const {subObjectsPermissionsErrorTitle} = this.props;
    const {permissions: granted = []} = this.state;
    const {subObjectsPermissionsMaskToCheck} = this.props;
    const {subObjectsPermissions} = this.state;
    const check = {
      read: roleModel.readPermissionEnabled(subObjectsPermissionsMaskToCheck),
      write: roleModel.writePermissionEnabled(subObjectsPermissionsMaskToCheck),
      execute: roleModel.executePermissionEnabled(subObjectsPermissionsMaskToCheck)
    };
    const warnings = [];
    for (let d = 0; d < granted.length; d++) {
      const {mask, sid = {}} = granted[d];
      const maskToCheck = mask & subObjectsPermissionsMaskToCheck;
      const {name, principal} = sid;
      const rolesToCheck = [];
      if (principal) {
        const userInfo = this.allUsers.find(u => u.name === name);
        if (userInfo && userInfo.roles) {
          rolesToCheck.push(
            ...(userInfo.roles || []).map(({name}) => ({name, principal: false}))
          );
        }
      } else {
        rolesToCheck.push({name: 'ROLE_USER', principal: false});
      }
      for (let o = 0; o < subObjectsPermissions.length; o++) {
        const subObjectPermission = subObjectsPermissions[o];
        const {
          read,
          write,
          execute
        } = roleModel.checkObjectPermissionsConflict(
          maskToCheck,
          sid,
          rolesToCheck,
          subObjectPermission.owner,
          subObjectPermission.permissions
        );
        if (check.read && read) {
          // Read conflict
          warnings.push((
            <span>
              {subObjectPermission.object.name}: read denied for <b>{name}</b>
            </span>
          ));
        }
        if (check.write && write) {
          // Write conflict
          warnings.push((
            <span>
              {subObjectPermission.object.name}: write denied for <b>{name}</b>
            </span>
          ));
        }
        if (check.execute && execute) {
          // Execute conflict
          warnings.push((
            <span>
              {subObjectPermission.object.name}: execute denied for <b>{name}</b>
            </span>
          ));
        }
      }
    }
    if (warnings.length > 0) {
      const title = subObjectsPermissionsErrorTitle && (
        <div style={{marginBottom: 5}}>
          {subObjectsPermissionsErrorTitle}
        </div>
      );
      const content = (
        <div>
          {
            warnings.map((warning, index) => (
              <div key={index}>
                {warning}
              </div>
            ))
          }
        </div>
      );
      if (warnings.length > MAX_SUB_OBJECTS_WARNINGS_TO_SHOW) {
        const rest = warnings.length - MAX_SUB_OBJECTS_WARNINGS_TO_SHOW;
        return (
          <Alert
            showIcon
            style={{marginBottom: 5}}
            message={(
              <div>
                {title}
                {
                  warnings.slice(0, MAX_SUB_OBJECTS_WARNINGS_TO_SHOW).map((warning, index) => (
                    <div key={index}>
                      {warning}
                    </div>
                  ))
                }
                <div>
                  <Popover
                    content={content}
                  >
                    <a>
                      ... and {rest} more {plural(rest, 'warning')}
                    </a>
                  </Popover>
                </div>
              </div>
            )}
            type="warning"
          />
        );
      }
      return (
        <Alert
          showIcon
          style={{marginBottom: 5}}
          message={(
            <div>
              {title}
              {content}
            </div>
          )}
          type="warning"
        />
      );
    }
  };

  renderUserName = (user) => {
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
          <Row>{user.userName}</Row>
          <Row><span style={{fontSize: 'smaller'}}>{attributesString}</span></Row>
        </Row>
      );
    } else {
      return user.userName;
    }
  };

  renderUserPermission = () => {
    const {
      selectedPermission,
      pending
    } = this.state;
    if (selectedPermission) {
      const {
        sid = {}
      } = selectedPermission;
      const {
        name,
        principal
      } = sid;
      const enabledMask = this.getEnabledMaskForSubject(name, principal);
      const columns = [
        {
          title: 'Permissions',
          dataIndex: 'permission',
          render: (name, item) => {
            if (!item.allowed && !item.denied) {
              return (<span>{name} <i style={{fontSize: 'smaller'}}>(inherit)</i></span>);
            }
            return name;
          }
        },
        {
          title: 'Allow',
          width: 50,
          className: styles.userAllowDenyActions,
          render: (item) => (
            <Checkbox
              disabled={
                pending ||
                this.props.readonly ||
                ((item.allowMask & enabledMask) === 0)
              }
              checked={item.allowed}
              onChange={
                this.onAllowDenyValueChanged(
                  item.allowMask | item.denyMask,
                  item.allowMask,
                  !item.isRead
                )
              }
            />
          )
        },
        {
          title: 'Deny',
          width: 50,
          className: styles.userAllowDenyActions,
          render: (item) => (
            <Checkbox
              disabled={
                pending ||
                this.props.readonly ||
                ((item.denyMask & enabledMask) === 0)
              }
              checked={item.denied}
              onChange={
                this.onAllowDenyValueChanged(item.allowMask | item.denyMask, item.denyMask)
              }
            />
          )
        }
      ];
      const data = [
        {
          permission: 'Read',
          allowMask: 1,
          denyMask: 1 << 1,
          allowed: roleModel.readAllowed(selectedPermission, true),
          denied: roleModel.readDenied(selectedPermission, true),
          isRead: true
        },
        {
          permission: 'Write',
          allowMask: 1 << 2,
          denyMask: 1 << 3,
          allowed: roleModel.writeAllowed(selectedPermission, true),
          denied: roleModel.writeDenied(selectedPermission, true)
        },
        {
          permission: 'Execute',
          allowMask: 1 << 4,
          denyMask: 1 << 5,
          allowed: roleModel.executeAllowed(selectedPermission, true),
          denied: roleModel.executeDenied(selectedPermission, true)
        }
      ];
      return (
        <Table
          style={{marginTop: 10}}
          key="user permissions"
          loading={pending}
          showHeader
          size="small"
          columns={columns}
          pagination={false}
          rowKey={(item) => item.permission}
          dataSource={data} />
      );
    }
    return undefined;
  };

  renderUsers = () => {
    const {
      pending,
      error,
      permissions: data = [],
      selectedPermission
    } = this.state;
    if (error) {
      return <Alert type="warning" message={error} />;
    }
    const getSidName = (name, principal) => {
      const {roles: rolesRequest} = this.props;
      const rolesList = (rolesRequest.loaded ? (rolesRequest.value || []) : []).map(r => r);
      if (principal) {
        return <UserName userName={name} />;
      } else {
        const [role] = rolesList.filter(r => !r.predefined && r.name === name);
        if (role) {
          return this.splitRoleName(name);
        } else {
          return name;
        }
      }
    };
    const columns = [
      {
        key: 'icon',
        className: styles.userIcon,
        render: (item) => {
          if (item.sid.principal) {
            return <Icon type="user" />;
          }
          return <Icon type="team" />;
        }
      },
      {
        dataIndex: 'sid.name',
        key: 'name',
        render: (name, item) => getSidName(name, item.sid.principal)
      },
      {
        key: 'actions',
        className: styles.userActions,
        render: (item) => (
          <Row>
            <Button
              disabled={pending || this.props.readonly}
              onClick={this.removeUserOrGroupClicked(item)}
              size="small">
              <Icon type="delete" />
            </Button>
          </Row>
        )
      }
    ];
    const getRowClassName = (item) => {
      if (!selectedPermission || selectedPermission.sid.name !== item.sid.name) {
        return styles.row;
      }
      return classNames(styles.selectedRow, 'cp-edit-permissions-selected-row');
    };
    const selectPermission = (item) => {
      this.setState({selectedPermission: item});
    };
    const title = (
      <Row>
        <Col span={12}>
          <b>Groups and users</b>
        </Col>
        <Col span={12} style={{textAlign: 'right'}}>
          {this.renderGroupAndUsersActions()}
        </Col>
      </Row>
    );
    return [
      <Table
        className={styles.table}
        key="users table"
        style={{
          maxHeight: 200,
          overflowY: 'auto'
        }}
        rowClassName={getRowClassName}
        onRowClick={selectPermission}
        loading={pending}
        title={() => title}
        showHeader={false}
        size="small"
        columns={columns}
        pagination={false}
        rowKey={(item) => item.sid.name}
        dataSource={(data || []).map(p => p)} />,
      this.renderUserPermission()
    ];
  };

  isAdmin = () => {
    if (!this.props.authenticatedUserInfo.loaded) {
      return false;
    }
    return this.props.authenticatedUserInfo.value.admin;
  };

  renderOwner = () => {
    const {
      pending,
      error,
      owner,
      ownerInput,
      originalOwner,
      fetchedUsers = []
    } = this.state;
    if (!pending && !error && originalOwner && this.props.showOwner) {
      const isAdminOrOwner = this.isAdmin() ||
        originalOwner === this.props.authenticatedUserInfo.value.userName;
      if (isAdminOrOwner) {
        const onBlur = () => {
          this.setState({
            ownerInput: undefined
          });
        };
        return (
          <Row
            className={styles.ownerContainer}
            type="flex"
            style={{margin: '0px 5px 10px', height: 22}}
            align="middle"
          >
            <span style={{marginRight: 5}}>Owner: </span>
            <AutoComplete
              size="small"
              style={{flex: 1}}
              placeholder="Change owner"
              optionLabelProp="text"
              value={
                ownerInput === undefined
                  ? owner
                  : ownerInput
              }
              onBlur={onBlur}
              onSelect={this.onUserSelect}
              onSearch={this.findUser}>
              {
                fetchedUsers.map(user => {
                  return (
                    <AutoComplete.Option
                      key={user.id}
                      text={user.userName}>
                      {this.renderUserName(user)}
                    </AutoComplete.Option>
                  );
                })
              }
            </AutoComplete>
          </Row>
        );
      }
      return (
        <Row
          className={styles.ownerContainer}
          type="flex"
          style={{margin: '0px 5px 10px', height: 22}}
          align="middle"
        >
          <span style={{marginRight: 5}}>Owner: </span>
          <b id="object-owner" style={{paddingLeft: 4}}>{owner}</b>
        </Row>
      );
    }
    return null;
  };

  revertChanges = () => {
    const {
      originalOwner,
      originalPermissions,
      selectedPermission
    } = this.state;
    this.setState({
      permissions: [...originalPermissions],
      selectedPermission: originalPermissions.find(findPermissionByPermission(selectedPermission)),
      owner: originalOwner,
      ownerInput: undefined
    });
  };

  applyChanges = () => {
    const {
      owner,
      originalOwner,
      permissions,
      originalPermissions
    } = this.state;
    const {
      objectType,
      objectIdentifier
    } = this.props;
    const changes = getPermissionChanges({
      owner,
      originalOwner,
      permissions,
      originalPermissions
    });
    if (changes.changed) {
      this.setState({pending: true});
      (async () => {
        const success = await applyPermissionChanges(changes, objectIdentifier, objectType);
        this.setState({pending: false}, () => {
          if (success) {
            this.objectChanged();
          }
        });
      })();
    }
  };

  render () {
    const {
      pending
    } = this.state;
    const {permissionsChanged} = this;
    return (
      <Row>
        {this.renderOwner()}
        {this.renderSubObjectsWarnings()}
        {this.renderUsers()}
        {!this.props.readonly && (
          <div className={styles.permissionsFormFooter}>
            <Button
              className={styles.permissionsFormAction}
              disabled={!permissionsChanged || pending}
              onClick={this.revertChanges}
            >
              REVERT
            </Button>
            <Button
              className={styles.permissionsFormAction}
              disabled={!permissionsChanged || pending}
              type="primary"
              onClick={this.applyChanges}
            >
              APPLY
            </Button>
          </div>
        )}
        <Modal
          title="Select user"
          onCancel={this.closeFindUserDialog}
          onOk={this.onSelectUser}
          footer={(
            <Row type="flex" justify="end">
              <Button
                onClick={this.closeFindUserDialog}
                style={{marginRight: 5}}
              >
                Cancel
              </Button>
              <Button
                type="primary"
                disabled={pending}
                onClick={this.onSelectUser}
              >
              OK
              </Button>
            </Row>
          )}
          visible={this.state.findUserVisible}>
          <Select
            disabled={!this.props.usersInfo.loaded}
            placeholder="Enter the account info"
            style={{width: '100%'}}
            showSearch
            value={this.state.selectedUser}
            onSelect={this.onUserFindInputChanged}
            filterOption={(input, option) => option.props.attributes
              .map(o => o.toLowerCase())
              .find(o => o.includes((input || '').toLowerCase()))
            }
            onSearch={(value) => this.setState({
              searchUserTouched: value.length > 2}
            )}
            onFocus={() => this.setState({searchUserTouched: false})}
            notFoundContent={this.state.searchUserTouched
              ? 'Not found'
              : 'Start typing to filter users...'
            }
          >
            {
              this.state.searchUserTouched ? (
                this.allUsers
                  .map(user => (
                    <Select.Option
                      key={user.name}
                      value={user.name}
                      attributes={
                        [
                          user.name,
                          ...Object.values(user.attributes || {})
                        ]
                      }
                    >
                      <UserName userName={user.name} />
                    </Select.Option>
                  ))
              ) : null
            }
          </Select>
        </Modal>
        <Modal
          title="Select group"
          onCancel={this.closeFindGroupDialog}
          onOk={this.onSelectGroup}
          footer={(
            <Row type="flex" justify="end">
              <Button
                onClick={this.closeFindGroupDialog}
                style={{marginRight: 5}}
              >
                Cancel
              </Button>
              <Button
                type="primary"
                disabled={pending}
                onClick={this.onSelectGroup}
              >
                OK
              </Button>
            </Row>
          )}
          visible={this.state.findGroupVisible}>
          <AutoComplete
            value={this.selectedGroup}
            style={{width: '100%'}}
            dataSource={this.findGroupDataSource()}
            onChange={this.onGroupFindInputChanged}
            placeholder="Enter the group name" />
        </Modal>
      </Row>
    );
  }

  selectFirstPermission = () => {
    const {permissions = []} = this.state;
    if (permissions && permissions.length > 0) {
      const [first] = permissions;
      this.setState({selectedPermission: first});
    }
  };

  fetchSubObjectsPermissions = () => {
    const wrapPermissionsFetch = (subObject) => new Promise((resolve) => {
      const request = new GetAllPermissions(subObject.entityId, subObject.entityClass);
      request.fetch()
        .then(() => {
          if (request.loaded) {
            const {owner, permissions = []} = request.value || {};
            resolve({object: subObject, permissions, owner});
          } else {
            resolve({object: subObject, permissions: []});
          }
        })
        .catch(() => {
          resolve({object: subObject, permissions: []});
        });
    });
    this.setState({
      subObjectsPermissions: []
    }, () => {
      Promise.all(
        (this.props.subObjectsToCheck || []).map(wrapPermissionsFetch)
      )
        .then(payloads => {
          this.setState({
            subObjectsPermissions: payloads
          });
        });
    });
  };

  componentDidMount () {
    this.objectChanged();
    this.fetchSubObjectsPermissions();
  }

  objectChanged = () => {
    const {
      objectIdentifier,
      objectType
    } = this.props;
    this._token = {};
    const token = this._token;
    this.setState({
      selectedPermission: undefined,
      pending: true,
      error: undefined,
      permissions: [],
      originalPermissions: [],
      originalOwner: undefined,
      owner: undefined,
      ownerInput: undefined
    });
    const commit = (fn) => {
      if (token === this._token) {
        fn();
      }
    };
    (async () => {
      try {
        const request = new GrantGet(objectIdentifier, objectType);
        await request.fetch();
        commit(() => {
          if (!request.loaded || request.error) {
            this.setState({
              error: request.error || 'Error fetching permissions',
              pending: false
            });
          } else {
            const {
              permissions,
              entity
            } = request.value || {};
            this.setState({
              permissions: (permissions || []).map((o) => ({...o})),
              originalPermissions: (permissions || []).map((o) => ({...o})),
              originalOwner: entity ? entity.owner : undefined,
              owner: entity ? entity.owner : undefined,
              pending: false,
              error: undefined
            }, () => this.selectFirstPermission());
          }
        });
      } catch (error) {
        commit(() => this.setState({
          pending: false,
          error: error.message
        }));
      }
    })();
  };

  componentDidUpdate (prevProps) {
    if (this.props.objectIdentifier !== prevProps.objectIdentifier) {
      this.objectChanged();
    }
    if (!compareSubObjects(this.props.subObjectsToCheck, prevProps.subObjectsToCheck)) {
      this.fetchSubObjectsPermissions();
    }
  }

  componentWillUnmount () {
    this._token = {};
  }
}
