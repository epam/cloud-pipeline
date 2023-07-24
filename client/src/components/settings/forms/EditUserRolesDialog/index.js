/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {
  Modal,
  Row,
  Button,
  message,
  Icon,
  Table,
  Select,
  Tabs
} from 'antd';
import UserInfoSummary from './UserInfoSummary';
import User from '../../../../models/user/User';
import Roles from '../../../../models/user/Roles';
import MetadataUpdateKeys from '../../../../models/metadata/MetadataUpdateKeys';
import MetadataDeleteKeys from '../../../../models/metadata/MetadataDeleteKeys';
import RoleAssign from '../../../../models/user/RoleAssign';
import RoleRemove from '../../../../models/user/RoleRemoveFromUser';
import UserUpdate from '../../../../models/user/UserUpdate';
import UserBlock from '../../../../models/user/UserBlock';
import Runners from '../../../../models/user/Runners';
import RunnersUpdate from '../../../../models/user/RunnersUpdate';
import {
  AssignCredentialProfiles,
  LoadEntityCredentialProfiles
} from '../../../../models/cloudCredentials';
import roleModel from '../../../../utils/roleModel';
import displayDate from '../../../../utils/displayDate';
import {
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY,
  SplitPanel
} from '../../../special/splitPanel';
import Metadata, {ApplyChanges} from '../../../special/metadata/Metadata';
import InstanceTypesManagementForm from '../InstanceTypesManagementForm';
import AWSRegionTag from '../../../special/AWSRegionTag';
import UserName from '../../../special/UserName';
import ShareWithForm from '../../../runs/logs/forms/ShareWithForm';
import {CP_CAP_RUN_CAPABILITIES} from '../../../pipelines/launch/form/utilities/parameters';
import MuteEmailNotifications from '../../../special/metadata/special/mute-email-notifications';
import PermissionsForm from '../../../roleModel/PermissionsForm';
import styles from './EditUserRolesDialog.css';

const RESTRICTED_METADATA_KEYS = [
  MuteEmailNotifications.metadataKey
].filter(Boolean);

@roleModel.authenticationInfo
@inject('dataStorages', 'metadataCache', 'cloudCredentialProfiles', 'impersonation', 'preferences')
@inject((common, params) => ({
  userInfo: params.user ? new User(params.user.id) : null,
  userId: params.user ? params.user.id : null,
  roles: new Roles(),
  credentialProfiles: params.user
    ? new LoadEntityCredentialProfiles(params.user.id, true)
    : null,
  runners: params.user ? new Runners(params.user.id) : null
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
    activeTab: 'user',
    selectedRole: null,
    defaultStorageId: undefined,
    defaultStorageIdInitial: undefined,
    defaultStorageInitialized: false,
    metadata: undefined,
    roles: [],
    rolesInitial: [],
    rolesInitialized: false,
    instanceTypesChanged: false,
    operationInProgress: false,
    profiles: [],
    profilesInitial: [],
    profilesInitialized: false,
    defaultProfileId: undefined,
    defaultProfileIdInitial: undefined,
    defaultProfileIdInitialized: false,
    runners: [],
    runnersInitial: [],
    runnersInitialized: false,
    shareDialogOpened: false
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

  @computed
  get isAdmin () {
    if (!this.props.authenticatedUserInfo.loaded) {
      return false;
    }
    const {
      admin
    } = this.props.authenticatedUserInfo.value;
    return admin;
  }

  onInstanceTypesFormInitialized = (form) => {
    this.instanceTypesForm = form;
  };

  updateValues = () => {
    const {
      defaultStorageInitialized,
      defaultProfileIdInitialized,
      rolesInitialized,
      profilesInitialized,
      runnersInitialized
    } = this.state;
    const state = {};
    if (!defaultStorageInitialized && this.props.userInfo && this.props.userInfo.loaded) {
      state.defaultStorageId = this.props.userInfo.value.defaultStorageId;
      state.defaultStorageIdInitial = this.props.userInfo.value.defaultStorageId;
      state.defaultStorageInitialized = true;
    }
    if (!defaultProfileIdInitialized && this.props.userInfo && this.props.userInfo.loaded) {
      state.defaultProfileId = this.props.userInfo.value.defaultProfileId;
      state.defaultProfileIdInitial = this.props.userInfo.value.defaultProfileId;
      state.defaultProfileIdInitialized = true;
    }
    if (!rolesInitialized && this.props.userInfo && this.props.userInfo.loaded) {
      state.roles = (this.props.userInfo.value.roles || []).map(r => r);
      state.rolesInitial = (this.props.userInfo.value.roles || []).map(r => r);
      state.rolesInitialized = true;
    }
    if (
      !profilesInitialized &&
      this.props.credentialProfiles &&
      this.props.credentialProfiles.loaded
    ) {
      state.profiles = (this.props.credentialProfiles.value || []).map(o => o.id);
      state.profilesInitial = (this.props.credentialProfiles.value || []).map(o => o.id);
      state.profilesInitialized = true;
    }
    if (
      !runnersInitialized &&
      this.props.runners &&
      this.props.runners.loaded
    ) {
      state.runners = (this.props.runners.value || []).slice();
      state.runnersInitial = (this.props.runners.value || []).slice();
      state.runnersInitialized = true;
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
              disabled={this.state.operationInProgress || !this.isAdmin}
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
      instanceTypesChanged: false,
      profiles: [],
      profilesInitial: [],
      profilesInitialized: false,
      defaultProfileId: undefined,
      defaultProfileIdInitial: undefined,
      defaultProfileIdInitialized: false,
      runners: [],
      runnersInitial: [],
      runnersInitialized: false
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

  get profilesModified () {
    const {profiles, profilesInitial} = this.state;
    const initial = [...(new Set(profilesInitial))];
    const current = [...(new Set(profiles))];
    if (initial.length === current.length) {
      for (let i = 0; i < initial.length; i++) {
        if (current.indexOf(initial[i]) === -1) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  get runnersModified () {
    const {
      runners = [],
      runnersInitial = []
    } = this.state;
    if (runnersInitial.length === runners.length) {
      for (let i = 0; i < runnersInitial.length; i++) {
        const runner = runnersInitial[i];
        if (
          !runners.find(r => r.name === runner.name &&
            r.accessType === runner.accessType &&
            r.principal === runner.principal
          )
        ) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return (this.props.dataStorages.value || [])
        .filter(d => roleModel.writeAllowed(d)).map(d => d);
    }
    return [];
  }

  @computed
  get cloudCredentialProfiles () {
    if (this.props.cloudCredentialProfiles.loaded) {
      return (this.props.cloudCredentialProfiles.value || [])
        .map(o => o);
    }
    return [];
  }

  get defaultStorageId () {
    const {dataStorages} = this.props;
    const {defaultStorageId} = this.state;
    if (defaultStorageId && dataStorages.loaded) {
      const dataStorage = (dataStorages.value || []).find(d => d.id === +defaultStorageId);
      if (!dataStorage) {
        return this.isAdmin ? undefined : `Access is denied`;
      }
      return `${defaultStorageId}`;
    }
    return undefined;
  }

  get defaultProfileId () {
    const {cloudCredentialProfiles} = this.props;
    const {defaultProfileId} = this.state;
    if (cloudCredentialProfiles.loaded) {
      const profile = (cloudCredentialProfiles.value || [])
        .find(d => d.id === +defaultProfileId);
      if (!profile) {
        return this.isAdmin ? undefined : `Access is denied`;
      }
      return `${defaultProfileId}`;
    }
    return undefined;
  }

  get modified () {
    const {
      metadata,
      defaultStorageId,
      defaultStorageIdInitial,
      instanceTypesChanged,
      defaultProfileId,
      defaultProfileIdInitial
    } = this.state;
    return !!metadata ||
      defaultStorageId !== defaultStorageIdInitial ||
      defaultProfileId !== defaultProfileIdInitial ||
      this.addedRoles.length > 0 ||
      this.removedRoles.length > 0 ||
      this.profilesModified ||
      this.runnersModified ||
      instanceTypesChanged;
  }

  @computed
  get restrictedMetadataKeys () {
    const {preferences} = this.props;
    return [
      ...RESTRICTED_METADATA_KEYS,
      ...(preferences.metadataSystemKeys || [])
    ];
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
    const {userName} = this.props.user;
    Modal.confirm({
      title: `Are you sure you want to ${block ? 'block' : 'unblock'} user ${userName}?`,
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

  openShareDialog = () => {
    this.setState({
      shareDialogOpened: true
    });
  };

  closeShareDialog = () => {
    this.setState({
      shareDialogOpened: false
    });
  };

  saveShareSids = async (sids = []) => {
    this.setState({
      runners: sids.map(sid => ({
        name: sid.name,
        principal: sid.isPrincipal,
        accessType: sid.accessType
      }))
    }, this.closeShareDialog);
  };

  saveChanges = async () => {
    if (this.modified) {
      const mainHide = message.loading('Updating user info...', 0);
      const {
        defaultStorageId,
        defaultStorageIdInitial,
        metadata,
        instanceTypesChanged,
        defaultProfileId,
        defaultProfileIdInitial,
        runners
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
      if (this.profilesModified || defaultProfileId !== defaultProfileIdInitial) {
        const hide = message.loading('Assigning credential profiles...', 0);
        const request = new AssignCredentialProfiles(
          this.props.userInfo.value.id,
          true,
          this.state.profiles,
          defaultProfileId
        );
        await request.send();
        hide();
        if (request.error) {
          message.error(request.error, 5);
          mainHide();
          return;
        }
      }
      if (this.runnersModified) {
        const hide = message.loading(
          (
            <span>
              Processing <i>Run As</i> permissions...
            </span>
          ), 0
        );
        const request = new RunnersUpdate(
          this.props.userInfo.value.id
        );
        await request.send(runners);
        hide();
        if (request.error) {
          message.error(request.error, 5);
          mainHide();
          return;
        }
      }
      if (
        this.addedRoles.length > 0 ||
        this.removedRoles.length > 0 ||
        this.profilesModified ||
        defaultProfileId !== defaultProfileIdInitial
      ) {
        await this.props.userInfo.fetch();
        await roleModel.refreshAuthenticationInfo(this);
      }
      if (metadata) {
        const hide = message.loading('Updating attributes...', 0);
        const fetchMetadata = this.props.metadataCache
          .getMetadata(this.props.userId, 'PIPELINE_USER');
        await fetchMetadata.fetchIfNeededOrWait();
        if (fetchMetadata.error) {
          hide();
          mainHide();
          message.error(fetchMetadata.error, 5);
          return;
        }
        const {data = {}} = (fetchMetadata.value || [])[0] || {};
        const modified = {};
        const removed = {};
        Object.keys(metadata || {})
          .forEach((key) => {
            if (!data.hasOwnProperty(key)) {
              modified[key] = {
                value: metadata[key].value,
                type: metadata[key].type
              };
            } else if (data[key].value !== metadata[key].value) {
              modified[key] = {
                value: metadata[key].value,
                type: metadata[key].type || data[key].type
              };
            }
          });
        Object.keys(data || {})
          .forEach(key => {
            if (!this.restrictedMetadataKeys.includes(key) && !metadata.hasOwnProperty(key)) {
              removed[key] = {
                value: data[key].value,
                type: data[key].type
              };
            }
          });
        if (Object.keys(removed).length > 0) {
          const removeRequest = new MetadataDeleteKeys();
          await removeRequest.send({
            entity: {
              entityId: this.props.userId,
              entityClass: 'PIPELINE_USER'
            },
            data: removed
          });
          if (removeRequest.error) {
            hide();
            mainHide();
            message.error(removeRequest.error, 5);
            return;
          }
        }
        if (Object.keys(modified).length > 0) {
          const updateRequest = new MetadataUpdateKeys();
          await updateRequest.send({
            entity: {
              entityId: this.props.userId,
              entityClass: 'PIPELINE_USER'
            },
            data: modified
          });
          if (updateRequest.error) {
            hide();
            mainHide();
            message.error(updateRequest.error, 5);
            return;
          }
        }
        hide();
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

  onChangeDefaultProfileId = (id) => {
    this.setState({defaultProfileId: id ? +id : undefined});
  };

  onChangeCredentialProfiles = (ids) => {
    let {defaultProfileId} = this.state;
    if (defaultProfileId && (ids || []).map(o => +o).indexOf(+defaultProfileId) === -1) {
      defaultProfileId = undefined;
    }
    this.setState({
      profiles: (ids || []).map(id => +id),
      defaultProfileId
    });
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
      defaultProfileIdInitial,
      rolesInitial,
      profilesInitial,
      runnersInitial
    } = this.state;
    this.setState({
      defaultStorageId: defaultStorageIdInitial,
      defaultProfileId: defaultProfileIdInitial,
      roles: rolesInitial.map(r => r),
      metadata: undefined,
      profiles: profilesInitial.slice(),
      runners: runnersInitial.slice()
    }, callback);
  };

  reload = () => {
    this.setState({
      defaultStorageInitialized: false,
      metadata: undefined,
      roles: [],
      rolesInitial: [],
      rolesInitialized: false,
      instanceTypesChanged: false,
      profiles: [],
      profilesInitial: [],
      profilesInitialized: false,
      defaultProfileIdInitialized: false,
      runners: [],
      runnersInitial: [],
      runnersInitialized: false,
      activeTab: 'user'
    }, () => this.revertChanges(this.updateValues));
  };

  renderRunners = () => {
    const {runners} = this.state;
    const {
      runners: runnersRequest,
      roles: rolesRequest
    } = this.props;
    if (runnersRequest && runnersRequest.pending && !runnersRequest.loaded) {
      return (
        <Icon type="loading" />
      );
    }
    const renderRole = (roleName) => {
      if (rolesRequest && rolesRequest.loaded) {
        const role = (rolesRequest.value || []).find(r => r.name === roleName);
        if (role && !role.predefined) {
          return this.splitRoleName(roleName);
        }
      }
      return roleName;
    };
    if (runners && runners.length > 0) {
      return runners
        .map((s, index, array) => {
          return (
            <span
              key={s.name}
              style={{marginRight: 5, whiteSpace: 'nowrap'}}
            >
              {
                s.principal
                  ? (<UserName userName={s.name} />)
                  : renderRole(s.name)
              }
              {
                index < array.length - 1 ? ',' : undefined
              }
            </span>
          );
        });
    }
    if (!this.isAdmin) {
      return 'not specified';
    }
    return 'configure';
  };

  onImpersonate = () => {
    const {user, impersonation} = this.props;
    if (user && user.userName && impersonation) {
      impersonation.impersonate(user.userName)
        .then(error => {
          if (error) {
            message.error(error, 5);
          }
        });
    }
  };

  renderUserInfo = () => {
    const {userName, registrationDate, attributes} = this.props.user || {};
    return (
      <div className={styles.userInfo}>
        <div className={styles.header}>
          <UserName userName={userName} />
        </div>
        <table className={styles.attributes}>
          <tbody>
            <tr>
              <td className={styles.info}>User name:</td>
              <td>{userName}</td>
            </tr>
            {
              registrationDate && (
                <tr>
                  <td className={styles.info}>Registration date:</td>
                  <td>{displayDate(registrationDate, 'd MMMM YYYY')}</td>
                </tr>
              )
            }
            {
              Object.entries(attributes || {})
                .map(([key, value]) => (
                  <tr key={key}>
                    <td className={styles.info}>{key}:</td>
                    <td>{value}</td>
                  </tr>
                ))
            }
          </tbody>
        </table>
      </div>
    );
  }

  renderUserRolesTab = () => {
    const {
      user
    } = this.props;
    const readOnly = !this.isAdmin;
    const metadataReadOnly = readOnly && !roleModel.writeAllowed(user);
    const {metadata} = this.state;
    const credentialProfilesPending = this.props.credentialProfiles
      ? this.props.credentialProfiles.pending
      : false;
    const runnersPending = this.props.runners
      ? this.props.runners.pending
      : false;
    return (
      <SplitPanel
        style={{flex: 1, height: 'unset', overflow: 'auto'}}
        contentInfo={[
          {
            key: CONTENT_PANEL_KEY,
            containerStyle: {
              display: 'flex',
              flexDirection: 'column',
              overflowX: 'hidden'
            },
            size: {
              priority: 0,
              percentMinimum: 33,
              percentDefault: 60
            }
          },
          {
            key: 'METADATA_AND_INSTANCE_MANAGEMENT',
            size: {
              keepPreviousSize: true,
              priority: 2,
              percentDefault: 40,
              pxMinimum: 200
            }
          }
        ]}>
        <div
          style={{display: 'flex', flexDirection: 'column', height: '100%'}}
          key={CONTENT_PANEL_KEY}>
          {this.renderUserInfo()}
          <Row type="flex" style={{marginBottom: 10}} align="middle">
            <span style={{marginRight: 5, fontWeight: 'bold', width: 160}}>
              Default data storage:
            </span>
            <Select
              allowClear
              showSearch
              disabled={this.state.operationInProgress || readOnly}
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
            <span style={{marginRight: 5, fontWeight: 'bold', width: 160}}>
              Add role or group:
            </span>
            <div style={{flex: 1}} id="find-role-select-container">
              <Select
                disabled={this.state.operationInProgress || readOnly}
                value={this.state.selectedRole}
                size="small"
                showSearch
                style={{width: '100%'}}
                allowClear
                placeholder="Add role or group"
                optionFilterProp="children"
                onChange={this.addRoleInputChanged}
                filterOption={
                  (input, option) =>
                    option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
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
                  this.state.operationInProgress ||
                  readOnly
                }>
                <Icon type="plus" /> Add
              </Button>
            </div>
          </Row>
          {this.renderUserRolesList()}
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
                overflow: 'auto'
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
            readOnly={this.state.operationInProgress || metadataReadOnly}
            key={METADATA_PANEL_KEY}
            entityId={this.props.userId}
            entityClass="PIPELINE_USER"
            applyChanges={ApplyChanges.callback}
            onChange={this.onChangeMetadata}
            value={metadata}
            extraKeys={[CP_CAP_RUN_CAPABILITIES]}
            restrictedKeys={this.restrictedMetadataKeys}
          />
          <div
            key="INSTANCE_MANAGEMENT"
          >
            <div style={{marginTop: 5, padding: 2}}>
              <span
                style={{fontWeight: 'bold', float: 'left'}}
              >
                Can run as this user:
              </span>
              <a
                onClick={readOnly ? undefined : this.openShareDialog}
                style={Object.assign({
                  marginLeft: 5,
                  wordBreak: 'break-word',
                  cursor: readOnly ? 'default' : 'pointer',
                  pointerEvents: readOnly ? 'none' : undefined
                })}
                className={
                  classNames({
                    'cp-text': readOnly
                  })
                }
              >
                {this.renderRunners()}
              </a>
              <ShareWithForm
                endpointsAvailable
                disabled={readOnly}
                visible={this.state.shareDialogOpened}
                roles={
                  this.props.roles.loaded
                    ? (this.props.roles.value || []).slice()
                    : []
                }
                sids={
                  this.state.runners.map(runner => ({
                    ...runner,
                    isPrincipal: runner.principal
                  }))
                }
                pending={runnersPending}
                onSave={this.saveShareSids}
                onClose={this.closeShareDialog}
              />
            </div>
            <InstanceTypesManagementForm
              className={styles.instanceTypesManagementForm}
              key="instance types management form"
              disabled={this.state.operationInProgress || readOnly}
              resourceId={this.props.userId}
              level="USER"
              onInitialized={this.onInstanceTypesFormInitialized}
              onModified={this.onInstanceTypesModified}
              showApplyButton={false}
            />
            <div style={{marginTop: 5, padding: 2, fontWeight: 'bold', width: 160}}>
              Cloud Credentials Profiles
            </div>
            <div
              style={{padding: '0 2px'}}
            >
              <Select
                allowClear
                showSearch
                mode="multiple"
                disabled={
                  this.state.operationInProgress ||
                  readOnly ||
                  credentialProfilesPending
                }
                value={this.state.profiles.map(o => `${o}`)}
                style={{width: '100%'}}
                onChange={this.onChangeCredentialProfiles}
                filterOption={(input, option) =>
                  option.props.name.toLowerCase().indexOf(input.toLowerCase()) >= 0
                }>
                {
                  this.cloudCredentialProfiles.map(d => (
                    <Select.Option
                      key={`${d.id}`}
                      value={`${d.id}`}
                      name={d.profileName}
                      title={d.profileName}
                    >
                      <AWSRegionTag
                        provider={d.cloudProvider}
                        showProvider
                        displayName={false}
                        displayFlag={false}
                      />
                      <span>{d.profileName}</span>
                    </Select.Option>
                  ))
                }
              </Select>
            </div>
            <div style={{marginTop: 5, padding: 2, fontWeight: 'bold', width: 160}}>
              Default Credentials Profile
            </div>
            <div
              style={{padding: '0 2px'}}
            >
              <Select
                allowClear
                showSearch
                disabled={
                  this.state.operationInProgress ||
                  readOnly ||
                  this.state.profiles.length === 0 ||
                  credentialProfilesPending
                }
                value={this.defaultProfileId}
                style={{width: '100%'}}
                onChange={this.onChangeDefaultProfileId}
                filterOption={(input, option) =>
                  option.props.name.toLowerCase().indexOf(input.toLowerCase()) >= 0
                }>
                {
                  this.cloudCredentialProfiles
                    .filter(d => this.state.profiles.indexOf(+d.id) >= 0)
                    .map(d => (
                      <Select.Option
                        key={`${d.id}`}
                        value={`${d.id}`}
                        name={d.profileName}
                        title={d.profileName}
                      >
                        <AWSRegionTag
                          provider={d.cloudProvider}
                          showProvider
                          displayName={false}
                          displayFlag={false}
                        />
                        <span>{d.profileName}</span>
                      </Select.Option>
                    ))
                }
              </Select>
            </div>
          </div>
        </SplitPanel>
      </SplitPanel>
    );
  };

  onChangeTab = (key) => {
    this.setState({activeTab: key});
  };

  renderFooter = () => {
    const readOnly = !this.isAdmin;
    const {activeTab} = this.state;
    let blocked = false;
    if (this.props.userInfo.loaded) {
      blocked = this.props.userInfo.value.blocked;
    }
    if (activeTab === 'user') {
      return (
        <Row type="flex" justify="space-between">
          <div>
            <Button
              disabled={readOnly}
              id="delete-user-button"
              type="danger"
              onClick={this.onDelete}>DELETE</Button>
            <Button
              disabled={readOnly}
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
              onClick={() => this.revertChanges()}
              disabled={readOnly || !this.modified}
            >
              REVERT
            </Button>
            <Button
              onClick={() => {
                this.revertChanges();
                this.onClose();
              }}
            >
              CANCEL
            </Button>
            <Button
              id="close-edit-user-form"
              type="primary"
              disabled={!this.modified}
              onClick={this.operationWrapper(this.saveChanges)}
            >
              OK
            </Button>
          </div>
        </Row>
      );
    }
    return (
      <Row type="flex" justify="end">
        <Button
          id="close-edit-user-form"
          type="primary"
          onClick={() => this.onClose()}
        >
          OK
        </Button>
      </Row>
    );
  };

  renderTabs = () => {
    const {activeTab} = this.state;
    let blocked = false;
    if (this.props.userInfo.loaded) {
      blocked = this.props.userInfo.value.blocked;
    }
    return (
      <Tabs
        activeKey={activeTab}
        onChange={this.onChangeTab}
      >
        <Tabs.TabPane
          tab={(
            <div>
              <span>
                PROFILE
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
          key="user"
        />
        {
          this.isAdmin && (
            <Tabs.TabPane
              tab="STATISTICS"
              key="user-statistics"
            />
          )
        }
        {
          this.isAdmin && (
            <Tabs.TabPane
              tab="PERMISSIONS"
              key="permissions"
            />
          )
        }
      </Tabs>
    );
  };

  renderContent = () => {
    const {
      user,
      userId
    } = this.props;
    const {activeTab} = this.state;
    if (activeTab === 'user-statistics') {
      return (
        <UserInfoSummary
          user={user}
          style={{flex: 1, overflow: 'auto'}}
        />
      );
    }
    if (activeTab === 'permissions') {
      return (
        <PermissionsForm
          objectType={'PIPELINE_USER'}
          objectIdentifier={userId}
          showOwner={false}
        />
      );
    }
    return this.renderUserRolesTab();
  };

  renderImpersonateButton = () => {
    const {activeTab} = this.state;
    const {user} = this.props;
    if (
      activeTab === 'user' &&
      user &&
      roleModel.executeAllowed(user)
    ) {
      return (
        <Button
          type="primary"
          onClick={this.onImpersonate}
          className={styles.impersonateBtn}
        >
          IMPERSONATE
        </Button>
      );
    }
    return null;
  };

  render () {
    if (!this.props.userInfo) {
      return null;
    }
    return (
      <Modal
        width="80%"
        closable={false}
        style={{
          top: 20
        }}
        bodyStyle={{
          height: '80vh'
        }}
        footer={this.renderFooter()}
        visible={this.props.visible}
      >
        <div className={styles.modalContainer}>
          {this.renderTabs()}
          {this.renderImpersonateButton()}
          {this.renderContent()}
        </div>
      </Modal>
    );
  }
}
