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
import {observer, inject} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Modal, Row, Button, message, Icon, Table, Select} from 'antd';
import User from '../../../../models/user/User';
import Roles from '../../../../models/user/Roles';
import MetadataUpdate from '../../../../models/metadata/MetadataUpdate';
import RoleAssign from '../../../../models/user/RoleAssign';
import RoleRemove from '../../../../models/user/RoleRemoveFromUser';
import UserUpdate from '../../../../models/user/UserUpdate';
import UserBlock from '../../../../models/user/UserBlock';
import styles from './UserManagement.css';
import roleModel from '../../../../utils/roleModel';
import {
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY,
  SplitPanel
} from '../../../special/splitPanel';
import Metadata, {ApplyChanges} from '../../../special/metadata/Metadata';
import InstanceTypesManagementForm from '../instance-types-management/InstanceTypesManagementForm';

@roleModel.authenticationInfo
@inject('dataStorages', 'metadataCache')
@inject((common, params) => ({
  userInfo: params.user ? new User(params.user.id) : null,
  userId: params.user ? params.user.id : null,
  roles: new Roles()
}))
@observer
export default class EditUserRolesDialog extends React.Component {
  static propTypes = {
    visible: PropTypes.bool,
    user: PropTypes.shape({
      id: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      userName: PropTypes.string
    }),
    onClose: PropTypes.func,
    onUserDelete: PropTypes.func
  };

  state = {
    selectedRole: null,
    defaultStorageId: undefined,
    defaultStorageIdInitial: undefined,
    defaultStorageInitialized: false,
    metadata: undefined,
    roles: [],
    rolesInitial: [],
    rolesInitialized: false,
    instanceTypesChanged: false,
    operationInProgress: false
  };

  instanceTypesForm;

  componentDidMount () {
    this.updateValues();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    this.updateValues();
  }

  onInstanceTypesFormInitialized = (form) => {
    this.instanceTypesForm = form;
  };

  updateValues = () => {
    const {defaultStorageInitialized, rolesInitialized} = this.state;
    const state = {};
    if (!defaultStorageInitialized && this.props.userInfo && this.props.userInfo.loaded) {
      state.defaultStorageId = this.props.userInfo.value.defaultStorageId;
      state.defaultStorageIdInitial = this.props.userInfo.value.defaultStorageId;
      state.defaultStorageInitialized = true;
    }
    if (!rolesInitialized && this.props.userInfo && this.props.userInfo.loaded) {
      state.roles = (this.props.userInfo.value.roles || []).map(r => r);
      state.rolesInitial = (this.props.userInfo.value.roles || []).map(r => r);
      state.rolesInitialized = true;
    }
    if (Object.keys(state).length > 0) {
      this.setState(state);
    }
  };

  splitRoleName = (name) => {
    if (name && name.toLowerCase().indexOf('role_') === 0) {
      return name.substring('role_'.length);
    }
    return name;
  };

  renderRolesList = (roles, actionComponentFn) => {
    const rolesSort = (roleA, roleB) => {
      if (roleA.name > roleB.name) {
        return 1;
      } else if (roleA.name < roleB.name) {
        return -1;
      }
      return 0;
    };
    const columns = [
      {
        dataIndex: 'name',
        className: 'role-name-column',
        key: 'name',
        render: (name, role) => role.predefined ? name : this.splitRoleName(name)
      },
      {
        key: 'action',
        className: 'role-actions-column',
        render: (role) => actionComponentFn && actionComponentFn(role)
      }
    ];
    return (
      <Table
        rowKey="id"
        locale={{emptyText: 'No roles found'}}
        className={styles.table}
        showHeader={false}
        pagination={false}
        loading={this.props.roles.pending || (this.props.userInfo && this.props.userInfo.pending)}
        columns={columns}
        dataSource={roles.sort(rolesSort)}
        rowClassName={role => `role-${role.name}`}
        size="small" />
    );
  };

  assignRole = () => {
    if (!this.props.roles.loaded) {
      return;
    }
    const {roles} = this.state;
    const [role] = (this.props.roles.value || [])
      .filter((r) => `${r.id}` === `${this.state.selectedRole}`);
    if (role) {
      roles.push(role);
      this.setState({roles, selectedRole: null});
    }
  };

  removeRole = (roleId) => {
    const {roles} = this.state;
    const [role] = roles
      .filter((r) => `${r.id}` === `${roleId}`);
    if (role) {
      const index = roles.indexOf(role);
      if (index >= 0) {
        roles.splice(index, 1);
        this.setState({roles});
      }
    }
  };

  renderUserRolesList = () => {
    const {roles, rolesInitialized} = this.state;
    if (!rolesInitialized) {
      return null;
    }
    return this.renderRolesList(
      roles,
      (role) => {
        return (
          <Row type="flex" justify="end">
            <Button
              id="delete-role-button"
              size="small"
              type="danger"
              onClick={() => this.removeRole(role.id)}
              disabled={this.state.operationInProgress}
            >
              <Icon type="delete" />
            </Button>
          </Row>
        );
      }
    );
  };

  onClose = () => {
    this.setState({
      search: null,
      defaultStorageId: undefined,
      defaultStorageIdInitial: undefined,
      defaultStorageInitialized: false,
      metadata: undefined,
      roles: [],
      rolesInitial: [],
      rolesInitialized: false,
      instanceTypesChanged: false
    }, this.props.onClose);
  };

  get availableRoles () {
    const {roles, rolesInitialized} = this.state;
    if (!rolesInitialized || !this.props.roles.loaded) {
      return [];
    }
    return (this.props.roles.value || [])
      .map(r => r)
      .filter(r => roles.filter(uR => uR.id === r.id).length === 0);
  }

  get addedRoles () {
    const {roles, rolesInitial} = this.state;
    return roles
      .filter(r => rolesInitial.filter(rI => rI.id === r.id).length === 0);
  }

  get removedRoles () {
    const {roles, rolesInitial} = this.state;
    return rolesInitial
      .filter(rI => roles.filter(r => r.id === rI.id).length === 0);
  }

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return (this.props.dataStorages.value || [])
        .filter(d => roleModel.writeAllowed(d)).map(d => d);
    }
    return [];
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
      this.addedRoles.length > 0 ||
      this.removedRoles.length > 0 ||
      instanceTypesChanged;
  }

  addRoleInputChanged = (id) => {
    this.setState({
      selectedRole: id
    });
  };

  onDelete = () => {
    const deleteUser = () => {
      this.props.onUserDelete && this.props.onUserDelete(this.props.user.id);
    };
    Modal.confirm({
      title: `Are you sure you want to delete user ${this.props.user.userName}?`,
      content: 'This operation cannot be undone.',
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        deleteUser();
      }
    });
  };

  onBlockUnBlock = (block) => {
    const blockUser = async () => {
      const hide = message.loading(
        `${block ? 'Blocking' : 'Unblocking'} user ${this.props.user.userName}...`
      );
      const request = new UserBlock(this.props.userId, block);
      await request.fetch();
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.props.userInfo.fetch();
        hide();
      }
    };
    Modal.confirm({
      title: `Are you sure you want to ${block ? 'block' : 'unblock'} user ${this.props.user.userName}?`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        blockUser();
      }
    });
  };

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

  saveChanges = async () => {
    if (this.modified) {
      const mainHide = message.loading('Updating user info...', 0);
      const {
        defaultStorageId,
        defaultStorageIdInitial,
        metadata,
        instanceTypesChanged
      } = this.state;
      if (defaultStorageId !== defaultStorageIdInitial) {
        const hide = message.loading('Updating default data storage...', -1);
        const request = new UserUpdate(this.props.userId);
        await request.send({defaultStorageId});
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
      if (this.addedRoles.length > 0) {
        const hide = message.loading('Assigning roles...', 0);
        const requests = this.addedRoles.map(role => new RoleAssign(
          role.id,
          this.props.userInfo.value.id
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
      if (this.removedRoles.length > 0) {
        const hide = message.loading('Removing roles...', 0);
        const requests = this.removedRoles.map(role => new RoleRemove(
          role.id,
          this.props.userInfo.value.id
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
      if (this.addedRoles.length > 0 || this.removedRoles.length > 0) {
        await this.props.userInfo.fetch();
        await roleModel.refreshAuthenticationInfo(this);
      }
      if (metadata) {
        const hide = message.loading('Updating attributes...', 0);
        const request = new MetadataUpdate();
        await request.send({
          entity: {
            entityId: this.props.userId,
            entityClass: 'PIPELINE_USER'
          },
          data: metadata
        });
        hide();
        if (request.error) {
          mainHide();
          message.error(request.error, 5);
          return;
        }
        await this.props.metadataCache.getMetadata(this.props.userId, 'PIPELINE_USER').fetch();
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

  revertChanges = () => {
    if (this.instanceTypesForm) {
      this.instanceTypesForm.reset();
    }
    const {
      defaultStorageIdInitial,
      rolesInitial
    } = this.state;
    this.setState({
      defaultStorageId: defaultStorageIdInitial,
      roles: rolesInitial.map(r => r),
      metadata: undefined
    });
  };

  render () {
    if (!this.props.userInfo) {
      return null;
    }
    let blocked = false;
    if (this.props.userInfo.loaded) {
      blocked = this.props.userInfo.value.blocked;
    }
    const {metadata} = this.state;
    return (
      <Modal
        width="50%"
        closable={false}
        title={(
          <div>
            <span>
              {this.props.user.userName}
            </span>
            {
              blocked &&
              <span
                style={{fontStyle: 'italic', marginLeft: 5}}
              >
                - blocked
              </span>
            }
          </div>
        )}
        footer={
          <Row type="flex" justify="space-between">
            <div>
              <Button
                id="delete-user-button"
                type="danger"
                onClick={this.onDelete}>DELETE</Button>
              <Button
                type="danger"
                onClick={() => this.onBlockUnBlock(!blocked)}
              >
                {
                  blocked ? 'UNBLOCK' : 'BLOCK'
                }
              </Button>
            </div>
            <div>
              <Button
                id="revert-changes-edit-user-form"
                onClick={this.revertChanges}
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
              <span style={{marginRight: 5, fontWeight: 'bold', width: 130}}>
                Default data storage:
              </span>
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
                        pathMask={d.pathMask}>
                        <b>{d.name}</b> ({d.pathMask})
                      </Select.Option>
                    );
                  })
                }
              </Select>
            </Row>
            <Row type="flex" style={{marginBottom: 10}} align="middle">
              <span style={{marginRight: 5, fontWeight: 'bold', width: 130}}>
                Add role or group:
              </span>
              <div style={{flex: 1}} id="find-role-select-container">
                <Select
                  disabled={this.state.operationInProgress}
                  value={this.state.selectedRole}
                  size="small"
                  showSearch
                  style={{width: '100%'}}
                  allowClear
                  placeholder="Add role or group"
                  optionFilterProp="children"
                  onChange={this.addRoleInputChanged}
                  filterOption={
                    (input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                  }>
                  {
                    this.availableRoles.map(t =>
                      <Select.Option
                        key={t.id}
                        value={`${t.id}`}>
                        {t.predefined ? t.name : this.splitRoleName(t.name)}
                      </Select.Option>
                    )
                  }
                </Select>
              </div>
              <div style={{paddingLeft: 10, textAlign: 'right'}}>
                <Button
                  id="add-role-button"
                  size="small"
                  onClick={this.assignRole}
                  disabled={
                    this.state.selectedRole === null ||
                    this.state.selectedRole === undefined ||
                    this.state.operationInProgress
                  }>
                  <Icon type="plus" /> Add
                </Button>
              </div>
            </Row>
            <Row type="flex" style={{height: '30vh', overflow: 'auto', display: 'flex', flexDirection: 'column'}}>
              {this.renderUserRolesList()}
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
                  flexDirection: 'column',
                  overflow: 'initial'
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
              entityId={this.props.userId}
              entityClass="PIPELINE_USER"
              applyChanges={ApplyChanges.callback}
              onChange={this.onChangeMetadata}
              value={metadata}
            />
            <InstanceTypesManagementForm
              disabled={this.state.operationInProgress}
              key="INSTANCE_MANAGEMENT"
              resourceId={this.props.userId}
              level="USER"
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
