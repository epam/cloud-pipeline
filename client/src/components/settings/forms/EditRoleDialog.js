/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {observer, inject} from 'mobx-react';
import PropTypes from 'prop-types';
import {Modal, Row, Button, message, Icon, Table, AutoComplete, Select} from 'antd';
import Role from '../../../models/user/Role';
import UserFind from '../../../models/user/UserFind';
import RoleAssign from '../../../models/user/RoleAssign';
import RoleRemove from '../../../models/user/RoleRemoveFromUser';
import RoleUpdate from '../../../models/user/RoleUpdate';
import GroupBlock from '../../../models/user/GroupBlock';
import MetadataUpdate from '../../../models/metadata/MetadataUpdate';
import styles from './UserManagement.css';
import roleModel from '../../../utils/roleModel';
import {
  SplitPanel,
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY
} from '../../special/splitPanel';
import Metadata, {ApplyChanges} from '../../special/metadata/Metadata';
import InstanceTypesManagementForm from './InstanceTypesManagementForm';

@roleModel.authenticationInfo
@inject('dataStorages', 'metadataCache')
@inject((common, params) => ({
  roleInfo: params.role ? new Role(params.role.id) : null,
  roleId: params.role ? params.role.id : null
}))
@observer
class EditRoleDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    role: PropTypes.shape({
      id: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      blocked: PropTypes.bool,
      name: PropTypes.string,
      predefined: PropTypes.bool
    }),
    onClose: PropTypes.func
  };

  state = {
    selectedUser: null,
    search: null,
    fetching: false,
    fetchedUsers: [],
    roleName: null,
    operationInProgress: false,
    defaultStorageId: undefined,
    defaultStorageIdInitial: undefined,
    defaultStorageInitialized: false,
    metadata: undefined,
    users: [],
    usersInitial: [],
    usersInitialized: false,
    instanceTypesChanged: false
  };

  instanceTypesForm;

  componentDidMount () {
    this.updateValues();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      this.reload();
    } else {
      this.updateValues();
    }
  }

  get defaultStorageId () {
    const {defaultStorageId} = this.state;
    if (defaultStorageId) {
      return `${defaultStorageId}`;
    }
    return undefined;
  }

  get modified () {
    const {metadata, defaultStorageId, defaultStorageIdInitial, instanceTypesChanged} = this.state;
    return !!metadata ||
      defaultStorageId !== defaultStorageIdInitial ||
      this.addedUsers.length > 0 ||
      this.removedUsers.length > 0 ||
      instanceTypesChanged;
  }

  get addedUsers () {
    const {users, usersInitial} = this.state;
    return users
      .filter(u => usersInitial.filter(uI => uI.userName === u.userName).length === 0);
  }

  get removedUsers () {
    const {users, usersInitial} = this.state;
    return usersInitial
      .filter(uI => users.filter(u => u.userName === uI.userName).length === 0);
  }

  onInstanceTypesFormInitialized = (form) => {
    this.instanceTypesForm = form;
  };

  updateValues = () => {
    const {defaultStorageInitialized, usersInitialized} = this.state;
    const state = {};
    if (!defaultStorageInitialized && this.props.roleInfo && this.props.roleInfo.loaded) {
      state.defaultStorageId = this.props.roleInfo.value.defaultStorageId;
      state.defaultStorageIdInitial = this.props.roleInfo.value.defaultStorageId;
      state.defaultStorageInitialized = true;
    }
    if (!usersInitialized && this.props.roleInfo && this.props.roleInfo.loaded) {
      state.users = (this.props.roleInfo.value.users || []).map(u => u);
      state.usersInitial = (this.props.roleInfo.value.users || []).map(u => u);
      state.usersInitialized = true;
    }
    if (Object.keys(state).length > 0) {
      this.setState(state);
    }
  };

  lastFetchId = 0;

  findUser = (value) => {
    this.lastFetchId += 1;
    const fetchId = this.lastFetchId;
    this.setState({
      search: value,
      fetching: true,
      selectedUser: null
    }, async () => {
      let assignedUsers = [];
      if (this.props.roleInfo.loaded) {
        assignedUsers = (this.props.roleInfo.value.users || []).map(r => r.userName.toUpperCase());
      }
      const request = new UserFind(value);
      await request.fetch();
      if (fetchId === this.lastFetchId) {
        let fetchedUsers = [];
        if (!request.error) {
          fetchedUsers = (request.value || [])
            .map(u => u)
            .filter(u => assignedUsers.indexOf(u.userName.toUpperCase()) === -1);
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
        selectedUser: user,
        fetchedUsers: [],
        search: user.userName
      });
    }
  };

  assignRole = () => {
    const {selectedUser, users} = this.state;
    if (selectedUser) {
      users.push(selectedUser);
      this.setState({users, selectedUser: null, fetchedUsers: [], search: null});
    }
  };

  removeRole = (userId) => {
    const {users} = this.state;
    const [user] = users.filter(u => u.id === userId);
    console.log('remove role', userId, user, users);
    if (user) {
      const index = users.indexOf(user);
      if (index >= 0) {
        users.splice(index, 1);
        this.setState({
          users,
          selectedUser: null,
          fetchedUsers: [],
          search: null
        });
      }
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

  renderUsersList = () => {
    const {users: roleUsers, usersInitialized} = this.state;
    if (!usersInitialized) {
      return null;
    }
    const usersSort = (userA, userB) => {
      if (userA.userName > userB.userName) {
        return 1;
      } else if (userA.userName < userB.userName) {
        return -1;
      }
      return 0;
    };
    const users = roleUsers.map(r => r).sort(usersSort);
    const columns = [
      {
        dataIndex: 'userName',
        key: 'name',
        className: 'user-name-column',
        render: (name, user) => {
          return this.renderUserName(user);
        }
      },
      {
        key: 'action',
        className: 'user-actions-column',
        render: (user) => {
          return (
            <Row type="flex" justify="end">
              <Button
                id="delete-user-button"
                size="small"
                type="danger"
                onClick={() => this.removeRole(user.id)}
              >
                <Icon type="delete" />
              </Button>
            </Row>
          );
        }
      }
    ];
    return (
      <Table
        rowKey="id"
        locale={{emptyText: 'No users found'}}
        className={styles.table}
        showHeader={false}
        pagination={false}
        loading={this.props.roleInfo && this.props.roleInfo.pending}
        columns={columns}
        dataSource={users}
        rowClassName={user => `user-${user.id}-row`}
        size="small" />
    );
  };

  onClose = () => {
    this.setState({
      search: null,
      operationInProgress: false,
      defaultStorageId: undefined,
      defaultStorageIdInitial: undefined,
      defaultStorageInitialized: false,
      metadata: undefined,
      users: [],
      usersInitial: [],
      usersInitialized: false,
      instanceTypesChanged: false
    }, this.props.onClose);
  };

  splitRoleName = (name) => {
    if (name && name.toLowerCase().indexOf('role_') === 0) {
      return name.substring('role_'.length);
    }
    return name;
  };

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return (this.props.dataStorages.value || [])
        .filter(d => roleModel.writeAllowed(d)).map(d => d);
    }
    return [];
  }

  operationWrapper = (fn) => {
    return (...opts) => {
      this.setState({
        operationInProgress: true
      }, async () => {
        await fn(...opts);
        this.setState({
          operationInProgress: false
        });
      });
    };
  };

  blockUnblockClicked = async () => {
    if (!this.props.roleInfo || !this.props.roleInfo.loaded) {
      return null;
    }
    const {blocked} = this.props.roleInfo.value;
    const blockStatus = !blocked;
    const blockGroup = async () => {
      const hide = message.loading(
        `${blockStatus ? 'Blocking' : 'Unblocking'} ${this.splitRoleName(this.props.role.name)}...`
      );
      const request = new GroupBlock(this.props.role.name, blockStatus);
      await request.send({});
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.props.roleInfo.fetch();
        hide();
      }
    };
    Modal.confirm({
      title: `Are you sure you want to ${blockStatus ? 'block' : 'unblock'} ${this.splitRoleName(this.props.role.name)}?`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        blockGroup();
      }
    });
  };

  saveChanges = async () => {
    if (this.modified) {
      const mainHide = message.loading('Updating role info...', 0);
      const {
        defaultStorageId,
        defaultStorageIdInitial,
        metadata,
        instanceTypesChanged
      } = this.state;
      if (defaultStorageId !== defaultStorageIdInitial) {
        const hide = message.loading('Updating default data storage...', -1);
        const request = new RoleUpdate(this.props.role.id);
        await request.send({
          name: this.props.role.name,
          userDefault: this.props.role.userDefault,
          defaultStorageId
        });
        if (request.error) {
          hide();
          message.error(request.error, 5);
          mainHide();
          return;
        } else {
          this.props.userInfo && await this.props.userInfo.fetch();
          hide();
        }
      }
      if (this.addedUsers.length > 0) {
        const hide = message.loading('Assigning role to users...', 0);
        const requests = this.addedUsers.map(user => new RoleAssign(
          this.props.role.id,
          user.id
        ));
        await Promise.all(requests.map(request => request.send({})));
        const [error] = requests
          .map(request => request.error)
          .filter(Boolean);
        hide();
        if (error) {
          message.error(error, 5);
          mainHide();
          return;
        }
      }
      if (this.removedUsers.length > 0) {
        const hide = message.loading('Removing role from users...', 0);
        const requests = this.removedUsers.map(user => new RoleRemove(
          this.props.role.id,
          user.id
        ));
        await Promise.all(requests.map(request => request.send({})));
        const [error] = requests
          .map(request => request.error)
          .filter(Boolean);
        hide();
        if (error) {
          mainHide();
          message.error(error, 5);
          return;
        }
      }
      if (this.addedUsers.length > 0 || this.removedUsers.length > 0) {
        await this.props.roleInfo.fetch();
        await roleModel.refreshAuthenticationInfo(this);
      }
      if (metadata) {
        const hide = message.loading('Updating attributes...', 0);
        const request = new MetadataUpdate();
        await request.send({
          entity: {
            entityId: this.props.role.id,
            entityClass: 'ROLE'
          },
          data: metadata
        });
        hide();
        if (request.error) {
          mainHide();
          message.error(request.error, 5);
          return;
        }
        await this.props.metadataCache.getMetadata(this.props.role.id, 'ROLE').fetch();
      }
      if (this.instanceTypesForm && instanceTypesChanged) {
        await this.instanceTypesForm.apply();
      }
      mainHide();
    }
    this.onClose();
  };

  onChangeMetadata = (metadata) => {
    this.setState({metadata});
  };

  onChangeDefaultStorageId = (id) => {
    this.setState({defaultStorageId: id ? +id : undefined});
  };

  onInstanceTypesModified = (modified) => {
    this.setState({instanceTypesChanged: modified});
  };

  revertChanges = (callback) => {
    if (this.instanceTypesForm) {
      this.instanceTypesForm.reset();
    }
    const {
      defaultStorageIdInitial,
      usersInitial
    } = this.state;
    this.setState({
      defaultStorageId: defaultStorageIdInitial,
      users: usersInitial.map(u => u),
      metadata: undefined
    }, callback);
  };

  reload = () => {
    this.setState({
      defaultStorageInitialized: false,
      metadata: undefined,
      roles: [],
      rolesInitial: [],
      rolesInitialized: false,
      instanceTypesChanged: false
    }, () => this.revertChanges(this.updateValues));
  };

  render () {
    if (!this.props.roleInfo) {
      return null;
    }
    let blocked = false;
    if (this.props.roleInfo.loaded) {
      blocked = this.props.roleInfo.value.blocked;
    }
    const {metadata} = this.state;
    return (
      <Modal
        width="50%"
        closable={false}
        title={(
          <Row>
            {
              this.props.role.predefined
                ? this.props.role.name
                : this.splitRoleName(this.props.role.name)
            }
            {
              blocked && (
                <span
                  style={{fontStyle: 'italic', marginLeft: 5}}
                >
                  - blocked
                </span>
              )
            }
          </Row>
        )}
        footer={
          <Row type="flex" justify="space-between">
            <Button
              id="edit-user-form-block-unblock"
              type="danger"
              onClick={this.operationWrapper(this.blockUnblockClicked)}>
              {blocked ? 'UNBLOCK' : 'BLOCK'}
            </Button>
            <div>
              <Button
                id="revert-changes-edit-user-form"
                onClick={() => this.revertChanges()}
                disabled={!this.modified}
              >
                REVERT
              </Button>
              <Button
                id="close-edit-user-form"
                type="primary"
                onClick={this.operationWrapper(this.saveChanges)}
              >
                OK
              </Button>
            </div>
          </Row>
        }
        visible={this.props.visible}>
        <SplitPanel
          contentInfo={[
            {
              key: CONTENT_PANEL_KEY,
              containerStyle: {
                display: 'flex',
                flexDirection: 'column'
              },
              size: {
                priority: 0,
                percentMinimum: 33,
                percentDefault: 75
              }
            },
            {
              key: 'METADATA_AND_INSTANCE_MANAGEMENT',
              size: {
                keepPreviousSize: true,
                priority: 2,
                percentDefault: 25,
                pxMinimum: 200
              }
            }
          ]}>
          <div
            style={{flex: 1, display: 'flex', flexDirection: 'column'}}
            key={CONTENT_PANEL_KEY}>
            <Row type="flex" style={{marginBottom: 10}} align="middle">
              <span style={{marginRight: 5, fontWeight: 'bold'}}>Default data storage:</span>
              <Select
                allowClear
                showSearch
                disabled={this.state.operationInProgress}
                value={this.defaultStorageId}
                style={{flex: 1}}
                onChange={this.onChangeDefaultStorageId}
                size="small"
                filterOption={(input, option) =>
                  option.props.name.toLowerCase().indexOf(input.toLowerCase()) >= 0 ||
                  option.props.pathMask.toLowerCase().indexOf(input.toLowerCase()) >= 0
                }>
                {
                  this.dataStorages.map(d => {
                    return (
                      <Select.Option
                        key={d.id}
                        value={`${d.id}`}
                        title={d.name}
                        name={d.name}
                        pathMask={d.pathMask}
                      >
                        <b>{d.name}</b> ({d.pathMask})
                      </Select.Option>
                    );
                  })
                }
              </Select>
            </Row>
            <Row type="flex" style={{marginBottom: 10}} align="middle">
              <div style={{flex: 1}} id="find-user-autocomplete-container">
                <AutoComplete
                  disabled={this.state.operationInProgress}
                  size="small"
                  style={{width: '100%'}}
                  placeholder="Search user"
                  optionLabelProp="text"
                  value={this.state.search}
                  onSelect={this.onUserSelect}
                  onSearch={this.findUser}>
                  {
                    this.state.fetchedUsers.map(user => {
                      return <AutoComplete.Option key={user.id} text={user.userName}>
                        {this.renderUserName(user)}
                      </AutoComplete.Option>;
                    })
                  }
                </AutoComplete>
              </div>
              <div style={{paddingLeft: 10, textAlign: 'right'}}>
                <Button
                  id="add-user-button"
                  size="small"
                  onClick={this.assignRole}
                  disabled={
                    this.state.selectedUser === null ||
                    this.state.selectedUser === undefined ||
                    this.state.operationInProgress
                  }>
                  <Icon type="plus" /> Add user
                </Button>
              </div>
            </Row>
            <Row type="flex"
              style={{
                height: '30vh',
                overflow: 'auto',
                display: 'flex',
                flexDirection: 'column'
              }}>
              {this.renderUsersList()}
            </Row>
          </div>
          <SplitPanel
            orientation="vertical"
            key="METADATA_AND_INSTANCE_MANAGEMENT"
            contentInfo={[
              {
                key: METADATA_PANEL_KEY,
                title: 'Attributes',
                containerStyle: {
                  display: 'flex',
                  flexDirection: 'column'
                },
                size: {
                  keepPreviousSize: true,
                  priority: 2,
                  percentDefault: 50,
                  pxMinimum: 200
                }
              },
              {
                key: 'INSTANCE_MANAGEMENT',
                title: 'Launch options',
                containerStyle: {
                  display: 'flex',
                  flexDirection: 'column'
                },
                size: {
                  keepPreviousSize: true,
                  priority: 2,
                  percentDefault: 50,
                  pxMinimum: 200
                }
              }
            ]}>
            <Metadata
              readOnly={this.state.operationInProgress}
              key={METADATA_PANEL_KEY}
              entityId={this.props.role.id}
              entityClass="ROLE"
              value={metadata}
              applyChanges={ApplyChanges.callback}
              onChange={this.onChangeMetadata}
            />
            <InstanceTypesManagementForm
              disabled={this.state.operationInProgress}
              key="INSTANCE_MANAGEMENT"
              resourceId={this.props.roleId}
              level="ROLE"
              onInitialized={this.onInstanceTypesFormInitialized}
              onModified={this.onInstanceTypesModified}
              showApplyButton={false}
            />
          </SplitPanel>
        </SplitPanel>
      </Modal>
    );
  }
}

export default EditRoleDialog;
