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
import {Table, Alert, Row, Col, Button, Icon, AutoComplete, Modal, message, Checkbox} from 'antd';
import GrantGet from '../../models/grant/GrantGet';
import GrantPermission from '../../models/grant/GrantPermission';
import GrantRemove from '../../models/grant/GrantRemove';
import GrantOwner from '../../models/grant/GrantOwner';
import UserFind from '../../models/user/UserFind';
import GroupFind from '../../models/user/GroupFind';
import Roles from '../../models/user/Roles';
import {inject, observer} from 'mobx-react';
import {observable} from 'mobx';
import styles from './PermissionsForm.css';
import roleModel from '../../utils/roleModel';
import UserName from '../special/UserName';

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
    selectedPermission: null,
    groupSearchString: null,
    selectedUser: null,
    owner: null,
    ownerInput: null,
    fetching: false,
    fetchedUsers: [],
    roleName: null,
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

  @observable
  userFind;
  @observable
  groupFind;

  static propTypes = {
    objectType: PropTypes.string,
    objectIdentifier: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    readonly: PropTypes.bool,
    defaultMask: PropTypes.number,
    enabledMask: PropTypes.number
  };

  static defaultProps = {
    enabledMask: roleModel.buildPermissionsMask(1, 1, 1, 1, 1, 1)
  }

  lastFetchId = 0;

  findUser = (value) => {
    this.lastFetchId += 1;
    const fetchId = this.lastFetchId;
    this.setState({
      ownerInput: value,
      owner: null,
      fetching: true,
      selectedUser: null
    }, async () => {
      const request = new UserFind(value);
      await request.fetch();
      if (fetchId === this.lastFetchId) {
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

  changeOwner = async () => {
    const userName = this.state.owner;
    const hide = message.loading(`Granting ${userName} owner permission...`, -1);
    const request = new GrantOwner(this.props.objectIdentifier, this.props.objectType, userName);
    await request.send({});
    if (request.error) {
      message.error(request.error);
      this.setState({
        selectedUser: null,
        fetchedUsers: [],
        owner: null,
        ownerInput: null
      }, hide);
    } else {
      await this.props.grant.fetch();
      this.setState({
        selectedUser: null,
        fetchedUsers: [],
        owner: null,
        ownerInput: null
      }, hide);
    }
  };

  clearOwnerInput = () => {
    this.setState({
      owner: null,
      ownerInput: null
    });
  };

  onUserFindInputChanged = (value) => {
    this.selectedUser = value;
    if (value && value.length) {
      this.userFind = new UserFind(value);
      this.userFind.fetch();
    } else {
      this.userFind = null;
    }
  };

  onGroupFindInputChanged = (value) => {
    this.selectedGroup = value;
    if (value && value.length) {
      this.groupFind = new GroupFind(value);
      this.groupFind.fetch();
    } else {
      this.groupFind = null;
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

  findUserDataSource = () => {
    if (this.userFind && !this.userFind.pending && !this.userFind.error) {
      const {permissions = []} = this.props.grant && this.props.grant.loaded
        ? (this.props.grant.value || {})
        : {};
      const existingUsers = new Set(
        permissions
          .filter(p => p.sid && p.sid.principal)
          .map(p => p.sid.name)
      );
      return (this.userFind.value || [])
        .filter(user => !existingUsers.has(user.userName))
        .map(user => user);
    }
    return [];
  };

  splitRoleName = (name) => {
    if (name && name.toLowerCase().indexOf('role_') === 0) {
      return name.substring('role_'.length);
    }
    return name;
  };

  findGroupDataSource = () => {
    const {permissions = []} = this.props.grant && this.props.grant.loaded
      ? (this.props.grant.value || {})
      : {};
    const existingGroups = new Set(
      permissions
        .filter(p => p.sid && !p.sid.principal)
        .map(p => p.sid.name)
    );
    const roles = (this.props.roles.loaded && this.state.groupSearchString) ? (
      (this.props.roles.value || [])
        .filter(r => r.name.toLowerCase().indexOf(this.state.groupSearchString.toLowerCase()) >= 0)
        .filter(r => !existingGroups.has(r.name))
        .map(r => r.predefined ? r.name : this.splitRoleName(r.name))
    ) : [];
    if (this.groupFind && !this.groupFind.pending && !this.groupFind.error) {
      return [...roles, ...(this.groupFind.value || []).map(g => g)];
    }
    return [...roles];
  };

  selectedUser = null;
  selectedGroup = null;

  openFindUserDialog = () => {
    this.selectedUser = null;
    this.setState({findUserVisible: true});
  };

  closeFindUserDialog = () => {
    this.setState({findUserVisible: false});
  };

  onSelectUser = async () => {
    await this.grantPermission(this.selectedUser, true, this.props.defaultMask || 0);
    this.closeFindUserDialog();
  };

  openFindGroupDialog = () => {
    this.selectedGroup = null;
    this.setState({findGroupVisible: true, groupSearchString: null});
  };

  closeFindGroupDialog = () => {
    this.setState({findGroupVisible: false, groupSearchString: null});
  };

  onSelectGroup = async () => {
    const [role] = (this.props.roles.loaded ? this.props.roles.value || [] : [])
      .filter(r => !r.predefined && this.splitRoleName(r.name) === this.selectedGroup);
    const roleName = role ? role.name : this.selectedGroup;
    await this.grantPermission(roleName, false, this.props.defaultMask || 0);
    this.closeFindGroupDialog();
  };

  grantPermission = async (name, principal, mask) => {
    const request = new GrantPermission();
    await request.send({
      aclClass: this.props.objectType.toUpperCase(),
      id: this.props.objectIdentifier,
      mask,
      principal,
      userName: name
    });
    if (request.error) {
      message.error(request.error);
    } else {
      await this.props.grant.fetch();
      if (this.props.grant.loaded) {
        const selectedPermission = (this.props.grant.value.permissions || [])
          .map(p => p)
          .find(p => p.sid && p.sid.name === name && p.sid.principal === principal);
        if (selectedPermission) {
          this.setState({selectedPermission});
        }
      }
    }
  };

  removeUserOrGroupClicked = (item) => async (event) => {
    event.stopPropagation();
    const request = new GrantRemove(
      this.props.objectIdentifier,
      this.props.objectType,
      item.sid.name,
      item.sid.principal
    );
    await request.fetch();
    if (request.error) {
      message.error(request.error);
    } else {
      await this.props.grant.fetch();
      if (this.state.selectedPermission) {
        if (this.state.selectedPermission.sid.name === item.sid.name &&
          this.state.selectedPermission.sid.principal === item.sid.principal) {
          this.setState({selectedPermission: null}, this.selectFirstPermission);
        }
      }
    }
  };

  onAllowDenyValueChanged = (permissionMask, allowDenyMask, allowRead = false) => async (event) => {
    const mask = (1 | 1 << 1 | 1 << 2 | 1 << 3 | 1 << 4 | 1 << 5) ^ permissionMask;
    let newValue = 0;
    if (event.target.checked) {
      newValue = allowDenyMask;
    }
    const selectedPermission = this.state.selectedPermission;
    selectedPermission.mask = (selectedPermission.mask & mask) | newValue;
    if (allowRead && event.target.checked) {
      selectedPermission.mask = (selectedPermission.mask & (1 << 2 | 1 << 3 | 1 << 4 | 1 << 5)) | 1;
    }
    const request = new GrantPermission();
    await request.send({
      aclClass: this.props.objectType.toUpperCase(),
      id: this.props.objectIdentifier,
      mask: selectedPermission.mask,
      principal: selectedPermission.sid.principal,
      userName: selectedPermission.sid.name
    });
    if (request.error) {
      message.error(request.error);
    } else {
      this.setState({selectedPermission});
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
    if (this.state.selectedPermission) {
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
              disabled={this.props.readonly || ((item.allowMask & this.props.enabledMask) === 0)}
              checked={item.allowed}
              onChange={this.onAllowDenyValueChanged(item.allowMask | item.denyMask, item.allowMask, !item.isRead)} />
          )
        },
        {
          title: 'Deny',
          width: 50,
          className: styles.userAllowDenyActions,
          render: (item) => (
            <Checkbox
              disabled={this.props.readonly || ((item.denyMask & this.props.enabledMask) === 0)}
              checked={item.denied}
              onChange={this.onAllowDenyValueChanged(item.allowMask | item.denyMask, item.denyMask)} />
          )
        }
      ];
      const data = [
        {
          permission: 'Read',
          allowMask: 1,
          denyMask: 1 << 1,
          allowed: roleModel.readAllowed(this.state.selectedPermission, true),
          denied: roleModel.readDenied(this.state.selectedPermission, true),
          isRead: true
        },
        {
          permission: 'Write',
          allowMask: 1 << 2,
          denyMask: 1 << 3,
          allowed: roleModel.writeAllowed(this.state.selectedPermission, true),
          denied: roleModel.writeDenied(this.state.selectedPermission, true)
        },
        {
          permission: 'Execute',
          allowMask: 1 << 4,
          denyMask: 1 << 5,
          allowed: roleModel.executeAllowed(this.state.selectedPermission, true),
          denied: roleModel.executeDenied(this.state.selectedPermission, true)
        }
      ];
      return (
        <Table
          style={{marginTop: 10}}
          key="user permissions"
          loading={this.props.grant.pending}
          showHeader={true}
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
    if (this.props.grant.error) {
      return <Alert type="warning" message={this.props.grant.error} />;
    }
    const data = this.props.grant.value && this.props.grant.value.permissions
      ? this.props.grant.value.permissions
      : [];
    const rolesList = (this.props.roles.loaded ? (this.props.roles.value || []) : []).map(r => r);
    const getSidName = (name, principal) => {
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
              disabled={this.props.readonly}
              onClick={this.removeUserOrGroupClicked(item)}
              size="small">
              <Icon type="delete" />
            </Button>
          </Row>
        )
      }
    ];
    const getRowClassName = (item) => {
      if (!this.state.selectedPermission || this.state.selectedPermission.sid.name !== item.sid.name) {
        return styles.row;
      } else {
        return styles.selectedRow;
      }
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
        loading={this.props.grant.pending}
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
    if (this.props.authenticatedUserInfo.loaded &&
      this.props.grant.loaded &&
      this.props.grant.value.entity &&
      this.props.grant.value.entity.owner) {
      const isAdminOrOwner = this.isAdmin() || this.props.grant.value.entity.owner === this.props.authenticatedUserInfo.value.userName;
      if (isAdminOrOwner) {
        const onBlur = () => {
          if (this.state.owner === null) {
            this.setState({
              ownerInput: null
            });
          }
        };
        return (
          <Row className={styles.ownerContainer} type="flex" style={{margin: '0px 5px 10px', height: 22}} align="middle">
            <span style={{marginRight: 5}}>Owner: </span>
            <AutoComplete
              size="small"
              style={{flex: 1}}
              placeholder="Change owner"
              optionLabelProp="text"
              value={this.state.ownerInput !== null ? this.state.ownerInput : this.props.grant.value.entity.owner}
              onBlur={onBlur}
              onSelect={this.onUserSelect}
              onSearch={this.findUser}>
              {
                this.state.fetchedUsers.map(user => {
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
            {
              this.state.owner && this.props.grant.value.entity.owner !== this.state.owner &&
              <Button
                id="change-owner-apply-button"
                type="primary"
                loading={this.state.operationInProgress}
                onClick={this.operationWrapper(this.changeOwner)}
                size="small"
                style={{marginLeft: 5}}>
                Apply
              </Button>
            }
            {
              this.state.owner && this.props.grant.value.entity.owner !== this.state.owner &&
              <Button
                id="change-owner-cancel-button"
                onClick={this.clearOwnerInput}
                size="small"
                style={{marginLeft: 5}}>
                Cancel
              </Button>
            }
          </Row>
        );
      } else {
        return (
          <Row className={styles.ownerContainer} type="flex" style={{margin: '0px 5px 10px', height: 22}} align="middle">
            <span style={{marginRight: 5}}>Owner: </span>
            <b id="object-owner" style={{paddingLeft: 4}}>{this.props.grant.value.entity.owner}</b>
          </Row>
        );
      }
    }
    return null;
  };

  render () {
    return (
      <Row>
        {this.renderOwner()}
        {this.renderUsers()}
        <Modal
          title="Select user"
          onCancel={this.closeFindUserDialog}
          onOk={this.onSelectUser}
          visible={this.state.findUserVisible}>
          <AutoComplete
            value={this.selectedUser}
            optionLabelProp="text"
            style={{width: '100%'}}
            onChange={this.onUserFindInputChanged}
            placeholder="Enter the account name">
            {
              (this.findUserDataSource() || []).map(user => {
                return (
                  <AutoComplete.Option key={user.userName} text={user.userName}>
                    {this.renderUserName(user)}
                  </AutoComplete.Option>
                );
              })
            }
          </AutoComplete>
        </Modal>
        <Modal
          title="Select group"
          onCancel={this.closeFindGroupDialog}
          onOk={this.onSelectGroup}
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
    if (this.props.grant) {
      this.props.grant.fetchIfNeededOrWait()
        .then(() => {
          if (this.props.grant.loaded) {
            const {permissions = []} = this.props.grant.value || {};
            if (permissions && permissions.length > 0) {
              const [first] = permissions;
              this.setState({selectedPermission: first});
            }
          }
        });
    }
  };

  componentDidMount () {
    this.selectFirstPermission();
  }

  componentDidUpdate (prevProps) {
    if (this.props.objectIdentifier !== prevProps.objectIdentifier) {
      this.setState({
        selectedPermission: null
      }, this.selectFirstPermission);
    }
  }
}
