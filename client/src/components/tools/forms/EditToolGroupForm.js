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
import {computed} from 'mobx';
import {Button, Form, Modal, Input, Row, Spin, Tabs} from 'antd';
import PermissionsForm from '../../roleModel/PermissionsForm';
import roleModel from '../../../utils/roleModel';

@inject('preferences')
@roleModel.authenticationInfo
@observer
export default class EditToolGroupForm extends React.Component {
  formRef = React.createRef();
  static propTypes = {
    toolGroup: PropTypes.shape({
      id: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      name: PropTypes.string,
      description: PropTypes.string,
      mask: PropTypes.number
    }),
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool
  };

  state = {
    activeTab: 'info'
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18}
    }
  };

  @computed
  get permissionsRestrictions () {
    const {
      preferences
    } = this.props;
    if (preferences.loaded) {
      return preferences.uiPersonalToolsPermissionsRestrictions;
    }
    return [];
  }

  handleSubmit = (e) => {
    e.preventDefault();
    this.formRef.current.validateFields()
      .then((values) => {
        this.props.onSubmit(values);
      })
      .catch(() => {});
  };

  validateGroupName = (rule, value) => {
    if (value && value.indexOf('___') >= 0) {
      return Promise.reject(new Error('You cannot use more than two underscores subsequently'));
    }
    return Promise.resolve();
  };

  renderForm = () => {
    const toolGroup = this.props.toolGroup;
    const formItems = [];
    if (toolGroup) {
      formItems.push((
        <Form.Item
          key="tool group id"
          style={{display: 'none'}}
          className="edit-tool-group-form-id-container"
          {...this.formItemLayout}
          name="id"
        >
          <Input disabled />
        </Form.Item>
      ));
    }
    formItems.push((
      <Form.Item
        key="tool group name"
        className="edit-tool-group-form-name-container"
        {...this.formItemLayout}
        label="Name"
        name="name"
        rules={[
          {required: true, message: 'Name is required'},
          {
            pattern: /^[\da-z]([\da-z\\.\-_]*[\da-z]+)*$/,
            message: 'Image name should contain only lowercase letters, digits, separators (-, ., _) and should not start or end with a separator'
          },
          {validator: this.validateGroupName}
        ]}
      >
        <Input
          disabled={!!toolGroup}
          ref={!toolGroup ? this.initializeNameInput : null}
          onPressEnter={this.handleSubmit}
        />
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        key="tool group description"
        className="edit-tool-group-form-description-container"
        {...this.formItemLayout}
        label="Description"
        name="description"
      >
        <Input
          type="textarea"
          ref={toolGroup ? this.initializeNameInput : null}
          disabled={this.props.pending}
        />
      </Form.Item>
    ));
    return formItems;
  };

  onSectionChange = (key) => {
    this.setState({activeTab: key});
  };

  isAdvancedUser = () => {
    const {
      authenticatedUserInfo
    } = this.props;
    if (authenticatedUserInfo.loaded) {
      const {
        roles = []
      } = authenticatedUserInfo.value;
      return roles.some(o => /^ROLE_ADVANCED_USER$/i.test(o.name));
    }
    return false;
  };

  render () {
    const {
      toolGroup,
      authenticatedUserInfo
    } = this.props;
    const isPersonal = toolGroup &&
      toolGroup.privateGroup &&
      roleModel.isOwner(this.props.toolGroup);
    const isAdmin = authenticatedUserInfo.loaded &&
      authenticatedUserInfo.value &&
      authenticatedUserInfo.value.admin;
    const isAdvancedUser = this.isAdvancedUser();
    const restrictions = isPersonal && !isAdmin && !isAdvancedUser
      ? this.permissionsRestrictions
      : [];
    const readOnlyRoles = restrictions.filter((r) => r.readonly).map((r) => r.role);
    const defaultMask = restrictions.map((rule) => ({
      role: rule.role,
      mask: rule.defaultMask
    }));
    const enabledMask = restrictions.map((rule) => ({
      role: rule.role,
      mask: rule.enabledMask
    }));
    const isNewToolGroup = this.props.toolGroup === undefined || this.props.toolGroup === null;
    const modalFooter = this.props.pending ? false : (
      <Row type="flex" justify="space-between">
        <Button
          disabled={this.props.pending}
          id="edit-tool-group-form-cancel-button"
          onClick={this.props.onCancel}>CANCEL</Button>
        <Button
          disabled={this.props.pending}
          id={`edit-tool-group-form-${isNewToolGroup ? 'create' : 'save'}-button`}
          type="primary" htmlType="submit"
          onClick={this.handleSubmit}>{isNewToolGroup ? 'CREATE' : 'SAVE'}</Button>
      </Row>
    );
    const onClose = () => {
      this.formRef.current && this.formRef.current.resetFields();
      this.setState({activeTab: 'info'});
    };
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title={
          isNewToolGroup
            ? 'Create group'
            : 'Edit group'
        }
        onCancel={this.props.onCancel}
        footer={this.state.activeTab === 'info' ? modalFooter : false}>
        <Spin spinning={this.props.pending}>
          <Form
            ref={this.formRef}
            className="edit-tool-group-form"
            initialValues={{
              id: toolGroup ? `${toolGroup.id}` : '',
              name: toolGroup ? toolGroup.name : '',
              description: toolGroup ? (toolGroup.description || '') : ''
            }}
          >
            <Tabs
              size="small"
              activeKey={this.state.activeTab}
              onChange={this.onSectionChange}>
              <Tabs.TabPane key="info" tab="Info">
                {this.renderForm()}
              </Tabs.TabPane>
              {
                this.props.toolGroup &&
                this.props.toolGroup.id &&
                roleModel.isOwner(this.props.toolGroup) && (
                  <Tabs.TabPane key="permissions" tab="Permissions">
                    <PermissionsForm
                      objectIdentifier={this.props.toolGroup.id}
                      objectType="TOOL_GROUP"
                      defaultMask={defaultMask}
                      enabledMask={enabledMask}
                      readOnlyRoles={readOnlyRoles}
                      editOwnerAvailable={
                        roleModel.isOwner(this.props.toolGroup) ||
                        roleModel.isManager.toolAdmin(this)
                      }
                    />
                  </Tabs.TabPane>
                )
              }
            </Tabs>
          </Form>
        </Spin>
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

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
  }
}
