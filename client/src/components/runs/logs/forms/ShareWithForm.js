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
import {Table, Row, Col, Button, Icon, AutoComplete, Modal, Checkbox} from 'antd';
import {AccessTypes} from '../../../../models/pipelines/PipelineRunUpdateSids';
import UserFind from '../../../../models/user/UserFind';
import GroupFind from '../../../../models/user/GroupFind';
import {observer} from 'mobx-react';
import {observable} from 'mobx';
import styles from './ShareWithForm.css';
import UserName from '../../../special/UserName';

@observer
export default class ShareWithForm extends React.Component {

  static propTypes = {
    endpointsAvailable: PropTypes.bool,
    sids: PropTypes.array,
    onSave: PropTypes.func,
    onClose: PropTypes.func,
    visible: PropTypes.bool,
    roles: PropTypes.array,
    pending: PropTypes.bool
  };

  state = {
    sids: [],
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
      return (this.userFind.value || []).map(user => user);
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
    const roles = this.state.groupSearchString
      ? (this.props.roles
          .filter(r => r.name.toLowerCase().indexOf(this.state.groupSearchString.toLowerCase()) >= 0)
          .map(r => r.predefined ? r.name : this.splitRoleName(r.name)))
      : [];
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
    await this.grantPermission(this.selectedUser, true);
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
    const [role] = this.props.roles
      .filter(r => !r.predefined && this.splitRoleName(r.name) === this.selectedGroup);
    const roleName = role ? role.name : this.selectedGroup;
    await this.grantPermission(roleName, false);
    this.closeFindGroupDialog();
  };

  grantPermission = async (name, isPrincipal) => {
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
  };

  removeUserOrGroupClicked = (item) => async (event) => {
    event.stopPropagation();
    const sids = this.state.sids;
    const [sidItem] = sids.filter(s => {
      return s.isPrincipal === item.isPrincipal && s.name.toLowerCase() === item.name.toLowerCase();
    });
    if (sidItem) {
      const index = sids.indexOf(sidItem);
      if (index >= 0) {
        sids.splice(index, 1);
        this.setState({sids});
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
    const data = this.state.sids.map((p, index) => {
      return {...p, id: index};
    });
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
