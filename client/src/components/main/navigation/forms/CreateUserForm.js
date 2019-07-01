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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import {Button, Modal, Form, Input, Row, Table, Icon, Select, message} from 'antd';
import styles from './UserManagement.css';
import roleModel from '../../../../utils/roleModel';

@Form.create()
@inject('dataStorages')
@observer
export default class CreateUserForm extends React.Component {

  static propTypes = {
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    roles: PropTypes.array
  };

  state = {
    selectedRole: null,
    selectedRoles: [],
    defaultRolesAssigned: false,
    search: null
  };

  @computed
  get dataStorages () {
    if (this.props.dataStorages.loaded) {
      return (this.props.dataStorages.value || []).filter(d => roleModel.writeAllowed(d)).map(d => d);
    }
    return [];
  }

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.props.onSubmit({
          userName: values.name,
          roleIds: this.state.selectedRoles,
          defaultStorageId: values.defaultStorageId
        });
      }
    });
  };

  availableRoles = () => {
    return (this.props.roles || [])
      .map(r => r)
      .filter(r => this.state.selectedRoles.filter(uR => uR === `${r.id}`).length === 0);
  };

  renderRolesList = (roles, actionComponentFn) => {
    const rolesSort = (roleA, roleB) => {
      if (roleA.displayName > roleB.displayName) {
        return 1;
      } else if (roleA.displayName < roleB.displayName) {
        return -1;
      }
      return 0;
    };
    const columns = [
      {
        dataIndex: 'displayName',
        className: 'role-name-column',
        key: 'name'
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
        locale={{emptyText: 'No groups or roles assigned'}}
        className={styles.table}
        showHeader={false}
        pagination={false}
        columns={columns}
        dataSource={roles.sort(rolesSort)}
        rowClassName={role => `role-${role.name}`}
        size="small" />
    );
  };

  assignRole = (id) => {
    const roles = this.state.selectedRoles;
    roles.push(`${id}`);
    this.setState({
      selectedRoles: roles,
      selectedRole: null
    });
  };

  removeRole = async (roleId) => {
    const roles = this.state.selectedRoles;
    const index = roles.indexOf(`${roleId}`);
    if (index >= 0) {
      roles.splice(index, 1);
      this.setState({
        selectedRoles: roles
      });
    }
  };

  renderUserRolesList = () => {
    const roles = (this.props.roles || [])
      .filter(r => this.state.selectedRoles.indexOf(`${r.id}`) >= 0)
      .map(r => {
        return {
          ...r,
          displayName: r.predefined ? r.name : this.splitRoleName(r.name)
        };
      });
    return this.renderRolesList(
      roles,
      (role) => {
        return (
          <Row type="flex" justify="end">
            <Button id="delete-role-button" size="small" type="danger" onClick={() => this.removeRole(role.id)}>
              <Icon type="delete" />
            </Button>
          </Row>
        );
      }
    );
  };

  splitRoleName = (name) => {
    if (name && name.toLowerCase().indexOf('role_') === 0) {
      return name.substring('role_'.length);
    }
    return name;
  };

  render () {
    const {getFieldDecorator, resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Button
          id="create-user-form-cancel-button"
          onClick={this.props.onCancel}>Cancel</Button>
        <Button
          id="create-user-form-ok-button"
          type="primary"
          htmlType="submit"
          onClick={this.handleSubmit}>Create</Button>
      </Row>
    );
    const onClose = () => {
      this.setState({
        selectedRole: null,
        selectedRoles: [],
        defaultRolesAssigned: false
      });
      resetFields();
    };
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title="Create user"
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Form className="create-user-form" layout="horizontal">
          <Form.Item
            style={{marginBottom: 5}}
            className="create-user-form-name-container"
            label="Name">
            {getFieldDecorator('name', {
              rules: [
                {required: true, message: 'Name is required'}
              ]
            })(
              <Input
                style={{width: '100%'}}
                ref={this.initializeNameInput}
                onPressEnter={this.handleSubmit}
                disabled={this.props.pending} />
            )}
          </Form.Item>
          <Form.Item
            style={{marginBottom: 5}}
            className="create-user-form-default-data-storage-container"
            label="Default data storage">
            {getFieldDecorator('defaultStorageId')(
              <Select
                allowClear
                showSearch
                disabled={this.props.pending}
                style={{flex: 1}}
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
                        name={d.name}
                        title={d.name}
                        pathMask={d.pathMask}>
                        <b>{d.name}</b> ({d.pathMask})
                      </Select.Option>
                    );
                  })
                }
              </Select>
            )}
          </Form.Item>
          <Row style={{marginTop: 15, paddingLeft: 2, marginBottom: 2}}>
            Assign group or role:
          </Row>
          <Row type="flex" style={{marginBottom: 10}} align="middle">
            <Select
              value={this.state.selectedRole}
              showSearch
              style={{flex: 1}}
              allowClear={true}
              placeholder="Add role"
              optionFilterProp="children"
              onSelect={this.assignRole}
              filterOption={
                (input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
              }>
              {
                this.availableRoles().map(t =>
                  <Select.Option
                    key={t.id}
                    value={`${t.id}`}>
                    {t.predefined ? t.name : this.splitRoleName(t.name)}
                  </Select.Option>
                )
              }
            </Select>
          </Row>
          <Row type="flex" style={{height: '30vh', overflow: 'auto', display: 'flex', flexDirection: 'column'}}>
            {this.renderUserRolesList()}
          </Row>
        </Form>
      </Modal>
    );
  }

  initializeNameInput = (input) => {
    if (input && input.refs && input.refs.input) {
      this.nameInput = input.refs.input;
      this.nameInput.onfocus = function () {
        setTimeout(() => {
          this.selectionStart = (this.value || '').length;
          this.selectionEnd = (this.value || '').length;
        }, 0);
      };
    }
  };

  focusNameInput = () => {
    if (this.props.visible && this.nameInput) {
      setTimeout(() => {
        this.nameInput.focus();
      }, 0);
    }
  };

  assignDefaultRoles = () => {
    this.setState({
      selectedRoles: this.props.roles.filter(r => r.userDefault).map(r => `${r.id}`),
      defaultRolesAssigned: true
    });
  };

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
    if (this.props.visible && !this.state.defaultRolesAssigned) {
      this.assignDefaultRoles();
    }
  }

  componentDidMount () {
    if (this.props.visible && !this.state.defaultRolesAssigned) {
      this.assignDefaultRoles();
    }
  }
}
