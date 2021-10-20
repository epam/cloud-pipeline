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
import {inject, observer} from 'mobx-react';
import {
  Alert,
  Checkbox,
  Popover,
  Row,
  Table,
  Button
} from 'antd';

import GrantGet from '../../../../models/grant/GrantGet';
import GetAllPermissions from '../../../..//models/grant/GetAllPermissions';
import Roles from '../../../..//models/user/Roles';
import roleModel from '../../../../utils/roleModel';
import UsersRolesSelect from '../../../special/users-roles-select';
import compareSubObjects from '../../../roleModel/utilities/compare-sub-objects';
import styles from './DataStorageSharingPermissionsForm.css';

function plural (count, noun) {
  return `${noun}${count > 1 ? 's' : ''}`;
}

const MAX_SUB_OBJECTS_WARNINGS_TO_SHOW = 5;

@inject('usersInfo')
@inject(({routing, authenticatedUserInfo}, params) => ({
  authenticatedUserInfo,
  grant: new GrantGet(params.objectIdentifier, params.objectType),
  roles: new Roles()
}))
@observer
export default class DataStorageSharingPermissionsForm extends React.Component {
  state = {
    selectedPermission: this.props.mask ? {mask: this.props.mask} : null,
    subObjectsPermissions: [],
    usersToShare: this.props.usersToShare || []
  };

  static propTypes = {
    objectIdentifier: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    mask: PropTypes.number,
    objectType: PropTypes.string,
    readonly: PropTypes.bool,
    subObjectsPermissionsMaskToCheck: PropTypes.number,
    subObjectsToCheck: PropTypes.arrayOf(PropTypes.shape({
      entityId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      entityClass: PropTypes.string,
      name: PropTypes.node,
      description: PropTypes.node
    })),
    subObjectsPermissionsErrorTitle: PropTypes.node,
    usersToShare: PropTypes.arrayOf(PropTypes.shape({
      name: PropTypes.string,
      principal: PropTypes.bool
    })),
    onChangeUsersToShare: PropTypes.func,
    goToCreateShareLink: PropTypes.func
  };

  static defaultProps = {
    subObjectsPermissionsMaskToCheck: 0,
    objectType: 'DATA_STORAGE'
  }

  onAllowDenyValueChanged = (permissionMask, allowDenyMask, allowRead = false) => async (event) => {
    const mask = (1 | 1 << 1 | 1 << 2 | 1 << 3) ^ permissionMask;
    let newValue = 0;
    if (event.target.checked) {
      newValue = allowDenyMask;
    }
    const selectedPermission = this.state.selectedPermission;
    selectedPermission.mask = (selectedPermission.mask & mask) | newValue;
    if (allowRead && event.target.checked) {
      selectedPermission.mask = (selectedPermission.mask & (1 << 2 | 1 << 3 | 2)) | 1;
    }
    this.setState({selectedPermission});
  };

  renderSubObjectsWarnings = () => {
    const {subObjectsPermissionsErrorTitle} = this.props;
    const users = this.props.usersInfo.loaded
      ? (this.props.usersInfo.value || []).slice()
      : [];
    const granted = this.props.grant.value && this.props.grant.value.permissions
      ? this.props.grant.value.permissions
      : [];
    const {subObjectsPermissionsMaskToCheck} = this.props;
    const {subObjectsPermissions} = this.state;
    const check = {
      read: roleModel.readPermissionEnabled(subObjectsPermissionsMaskToCheck),
      write: roleModel.writePermissionEnabled(subObjectsPermissionsMaskToCheck)
    };
    const warnings = [];
    for (let d = 0; d < granted.length; d++) {
      const {mask, sid = {}} = granted[d];
      const maskToCheck = mask & subObjectsPermissionsMaskToCheck;
      const {name, principal} = sid;
      const rolesToCheck = [];
      if (principal) {
        const userInfo = users.find(u => u.name === name);
        if (userInfo && userInfo.roles) {
          rolesToCheck.push(
            ...(userInfo.roles || []).map(({name}) => ({name, principal: false}))
          );
        }
      }
      for (let o = 0; o < subObjectsPermissions.length; o++) {
        const subObjectPermission = subObjectsPermissions[o];
        const {
          read,
          write
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
              disabled={
                item.allowMask === 0 ||
                this.state.usersToShare?.length === 0
              }
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
              disabled={
                item.denyMask === 0 ||
                this.state.usersToShare?.length === 0
              }
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
        }
      ];
      return (
        <Table
          style={{marginTop: 10}}
          key="user permissions"
          loading={!this.props.grant.loaded && this.props.grant.pending}
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
    if (this.props.grant.error) {
      return <Alert type="warning" message={this.props.grant.error} />;
    }
    return (
      <div>
        <div
          style={{
            width: '100%',
            position: 'relative'
          }}
        >
          <UsersRolesSelect
            value={this.state.usersToShare}
            onChange={(value) => this.props.onChangeUsersToShare(value)}
            style={{flex: 1, width: '100%'}}
          />
        </div>
        {this.renderUserPermission()}
      </div>
    );
  };

  render () {
    return (
      <div>
        <Row>
          {this.renderSubObjectsWarnings()}
          {this.renderUsers()}
        </Row>
        <Row type="flex" justify="end">
          <Button
            type="primary"
            style={{marginTop: '10px'}}
            disabled={this.state.usersToShare.length === 0}
            onClick={() => this.props.goToCreateShareLink(this.state.selectedPermission)}
          >SAVE AND SHARE</Button>
        </Row>
      </div>
    );
  }

  // selectFirstPermission = () => {
  //   this.setState({selectedPermission: {mask: 0}});
  // };

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
    this.fetchSubObjectsPermissions();
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.usersToShare !== this.props.usersToShare) {
      this.setState({
        usersToShare: nextProps.usersToShare
      });
    }
    if (this.props.objectIdentifier !== nextProps.objectIdentifier) {
      this.setState({
        selectedPermission: null
      });
    }
  }

  componentDidUpdate (prevProps) {
    if (!compareSubObjects(this.props.subObjectsToCheck, prevProps.subObjectsToCheck)) {
      this.fetchSubObjectsPermissions();
    }
  }
}
