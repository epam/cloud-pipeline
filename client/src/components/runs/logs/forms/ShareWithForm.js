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
import {Table, Row, Col, Button, Icon, AutoComplete, Modal, Checkbox, message} from 'antd';
import {AccessTypes} from '../../../../models/pipelines/PipelineRunUpdateSids';
import UserFind from '../../../../models/user/UserFind';
import GroupFind from '../../../../models/user/GroupFind';
import {observer} from 'mobx-react';
import {observable} from 'mobx';
import styles from './ShareWithForm.css';
import UserName from '../../../special/UserName';

function sortByOverlap (str1, str2, query) {
  if (str1.toLowerCase().indexOf(query) > str2.toLowerCase().indexOf(query)) {
    return 1;
  }
  if (str2.toLowerCase().indexOf(query) > str1.toLowerCase().indexOf(query)) {
    return -1;
  }
  if (
    str2.toLowerCase().indexOf(query) === str1.toLowerCase().indexOf(query) &&
    str1.toLowerCase().indexOf(query) >= 0
  ) {
    return str1.toLowerCase() > str2.toLowerCase() ? 1 : -1;
  }
  return 0;
}

export const ROLE_ALL = {
  name: 'ALL ROLES',
  includedRoles: ['ROLE_USER', 'ROLE_ADMIN', 'ROLE_ANONYMOUS_USER']
};

export function shouldCombineRoles (sids, combinableRoles, accessType) {
  if (!sids || !combinableRoles || !accessType) {
    return false;
  }
  return combinableRoles
    .every(roleName => !!sids
      .find((role) => !role.isPrincipal &&
        role.accessType === accessType &&
        role.name === roleName
      )
    );
};

@observer
export default class ShareWithForm extends React.Component {
  static propTypes = {
    endpointsAvailable: PropTypes.bool,
    sids: PropTypes.array,
    onSave: PropTypes.func,
    onClose: PropTypes.func,
    visible: PropTypes.bool,
    roles: PropTypes.array,
    pending: PropTypes.bool,
    runSharing: PropTypes.bool
  };

  state = {
    sids: [],
    findUserVisible: false,
    findGroupVisible: false,
    selectedPermission: null,
    groupSearchString: null,
    userSearchString: null,
    fetching: false,
    fetchedUsers: [],
    roleName: null,
    operationInProgress: false
  };

  get combineRolesIntoAllRoles () {
    const {sids} = this.state;
    const {runSharing} = this.props;
    return {
      ssh: runSharing && shouldCombineRoles(sids, ROLE_ALL.includedRoles, AccessTypes.ssh),
      endpoint: runSharing && shouldCombineRoles(sids, ROLE_ALL.includedRoles, AccessTypes.endpoint)
    };
  }

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

  @observable userFind;
  @observable groupFind;

  onUserFindInputChanged = (value) => {
    if (value) {
      this.userFind = new UserFind(value);
      this.userFind.fetch();
    } else {
      this.userFind = null;
    }
    this.setState({userSearchString: value});
  };

  onGroupFindInputChanged = (value) => {
    if (value) {
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
        <Button disabled={this.props.pending} size="small" onClick={this.openFindUserDialog}>
          <Icon type="user-add" />
        </Button>
        <Button disabled={this.props.pending} size="small" onClick={this.openFindGroupDialog}>
          <Icon type="usergroup-add" />
        </Button>
      </span>
    );
  };

  findUserDataSource = () => {
    if (this.userFind && !this.userFind.pending && !this.userFind.error) {
      return (this.userFind.value || []).map(user => user)
        .sort((u1, u2) => {
          const query = this.state.userSearchString?.toLowerCase().trim();
          return sortByOverlap(u1.userName, u2.userName, query);
        });
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
    const query = this.state.groupSearchString?.toLowerCase().trim();
    const roles = this.state.groupSearchString
      ? (
        this.props.roles
          .map(r => r.predefined ? r.name : this.splitRoleName(r.name))
          .filter(name => name.toLowerCase()
            .indexOf(this.state.groupSearchString.toLowerCase()) >= 0
          )
      )
      : [];
    if (this.groupFind && !this.groupFind.pending && !this.groupFind.error) {
      const set = [...new Set([
        ...(this.props.runSharing ? [ROLE_ALL.name] : []),
        ...roles,
        ...(this.groupFind.value || []).map(g => g)
      ])];
      return set
        .sort((u1, u2) => sortByOverlap(u1, u2, query));
    }
    return [...roles]
      .sort((u1, u2) => sortByOverlap(u1, u2, query));
  };

  openFindUserDialog = () => {
    this.setState({findUserVisible: true, userSearchString: null});
  };

  closeFindUserDialog = () => {
    this.setState({findUserVisible: false, userSearchString: null});
  };

  onSelectUser = async () => {
    const {userSearchString} = this.state;
    const selectedUser = userSearchString ? userSearchString.trim() : null;
    this.grantPermission(selectedUser, true);
    this.closeFindUserDialog();
  };

  openFindGroupDialog = () => {
    this.setState({findGroupVisible: true, groupSearchString: null});
  };

  closeFindGroupDialog = () => {
    this.setState({findGroupVisible: false, groupSearchString: null});
  };

  onSelectGroup = async () => {
    const {groupSearchString} = this.state;
    const selectedGroup = groupSearchString ? groupSearchString.trim() : null;
    const [role] = this.props.roles
      .filter(r => !r.predefined && this.splitRoleName(r.name) === selectedGroup);
    const roleName = role ? role.name : selectedGroup;
    if (roleName === ROLE_ALL.name) {
      ROLE_ALL.includedRoles.forEach(role => this.grantPermission(role, false));
    } else {
      this.grantPermission(roleName, false);
    }
    this.closeFindGroupDialog();
  };

  grantPermission = (name, isPrincipal) => {
    if (name) {
      const sids = this.state.sids;
      const [sidItem] = sids.filter(s => {
        return s.isPrincipal === isPrincipal && s.name.toLowerCase() === name.toLowerCase();
      });
      if (!sidItem) {
        sids.push({
          accessType: this.props.endpointsAvailable ? AccessTypes.endpoint : AccessTypes.ssh,
          name,
          isPrincipal
        });
        this.setState({sids});
      }
    } else {
      message.warning('Please provide non empty string!');
    }
  };

  removeUserOrGroupClicked = (item) => async (event) => {
    event.stopPropagation();
    const {sids} = this.state;
    const filterSids = ({name, isPrincipal}) => {
      if (item.name === ROLE_ALL.name) {
        return !ROLE_ALL.includedRoles.includes(name);
      }
      return !(name.toLowerCase() === item.name.toLowerCase() &&
        isPrincipal === item.isPrincipal);
    };
    this.setState({sids: sids.filter(filterSids)});
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

  renderUsers = () => {
    const getSidName = (name, isPrincipal) => {
      if (isPrincipal) {
        return <UserName userName={name} />;
      } else {
        const [role] = this.props.roles.filter(r => !r.predefined && r.name === name);
        if (role) {
          return this.splitRoleName(name);
        } else {
          return name;
        }
      }
    };
    const changeAccessLevel = (item) => (e) => {
      const {sids} = this.state;
      sids[item.id].accessType = e.target.checked ? AccessTypes.ssh : AccessTypes.endpoint;
      this.setState({sids});
    };
    const columns = [
      {
        key: 'icon',
        className: styles.userIcon,
        render: (item) => {
          if (item.isPrincipal) {
            return <Icon type="user" />;
          }
          return <Icon type="team" />;
        }
      },
      {
        dataIndex: 'name',
        key: 'name',
        render: (name, item) => getSidName(name, item.isPrincipal)
      },
      this.props.endpointsAvailable
        ? {
          dataIndex: 'accessType',
          key: 'ssh',
          render: (level, item) => (
            <Checkbox
              checked={level === AccessTypes.ssh}
              onChange={changeAccessLevel(item)}
            >
              Enable SSH connection
            </Checkbox>
          )
        }
        : false,
      {
        key: 'actions',
        className: styles.userActions,
        render: (item) => (
          <Row>
            <Button
              disabled={this.props.pending}
              onClick={this.removeUserOrGroupClicked(item)}
              size="small">
              <Icon type="delete" />
            </Button>
          </Row>
        )
      }
    ].filter(Boolean);
    const getRowClassName = (item) => {
      if (!this.state.selectedPermission || this.state.selectedPermission.name !== item.name) {
        return styles.row;
      } else {
        return 'cp-table-element-selected';
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
    let data = this.state.sids.map((p, index) => {
      return {...p, id: index};
    });
    const {
      ssh: combineSshRoles,
      endpoint: combineEndpointRoles
    } = this.combineRolesIntoAllRoles;
    if (combineSshRoles || combineEndpointRoles) {
      data = [
        ...data,
        {
          ...ROLE_ALL,
          key: ROLE_ALL.name,
          id: data.length
        }
      ].filter(({name, accessType}) => {
        if (
          (combineSshRoles && accessType === AccessTypes.ssh) ||
          (combineEndpointRoles && accessType === AccessTypes.endpoint)
        ) {
          return !ROLE_ALL.includedRoles.includes(name);
        }
        return true;
      });
    }
    return (
      <Table
        className={styles.table}
        key="users table"
        style={{
          maxHeight: 200,
          overflowY: 'auto'
        }}
        rowClassName={getRowClassName}
        onRowClick={selectPermission}
        title={() => title}
        showHeader={false}
        size="small"
        columns={columns}
        pagination={false}
        rowKey={(item) => item.id}
        dataSource={data} />
    );
  };

  onSave = () => {
    this.props.onSave && this.props.onSave(this.state.sids);
  };

  render () {
    return (
      <Modal
        visible={this.props.visible}
        onOk={this.onSave}
        onCancel={this.props.onClose}
        footer={this.props.pending ? false : undefined}
        title="Share with users and groups">
        <Row>
          {this.renderUsers()}
          <Modal
            title="Select user"
            onCancel={this.closeFindUserDialog}
            onOk={this.onSelectUser}
            visible={this.state.findUserVisible}>
            <AutoComplete
              value={this.state.userSearchString}
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
              value={this.state.groupSearchString}
              style={{width: '100%'}}
              dataSource={this.findGroupDataSource()}
              onChange={this.onGroupFindInputChanged}
              placeholder="Enter the group name" />
          </Modal>
        </Row>
      </Modal>
    );
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.visible !== this.props.visible) {
      this.setState({
        sids: this.props.sids || []
      });
    }
  }
}
