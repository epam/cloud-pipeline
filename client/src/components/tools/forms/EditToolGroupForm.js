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
import {Button, Modal, Form, Input, Row, Spin, Tabs} from 'antd';
import PermissionsForm from '../../roleModel/PermissionsForm';
import roleModel from '../../../utils/roleModel';

@Form.create()
export default class EditToolGroupForm extends React.Component {

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

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.props.onSubmit(values);
      }
    });
  };

  validateGroupName = (value, callback) => {
    if (value && value.indexOf('___') >= 0) {
      callback('You cannot use more than two underscores subsequently');
    } else {
      callback();
    }
  };

  renderForm = () => {
    const {getFieldDecorator} = this.props.form;
    const formItems = [];
    if (this.props.toolGroup) {
      formItems.push((
        <Form.Item
          key="tool group id"
          style={{display: 'none'}}
          className="edit-tool-group-form-id-container"
          {...this.formItemLayout}>
          {getFieldDecorator('id',
            {
              initialValue: `${this.props.toolGroup ? this.props.toolGroup.id : ''}`
            })(
            <Input disabled={true} />
          )}
        </Form.Item>
      ));
    }
    formItems.push((
      <Form.Item
        key="tool group name"
        className="edit-tool-group-form-name-container"
        {...this.formItemLayout} label="Name">
        {getFieldDecorator('name',
          {
            rules: [
              {
                required: true,
                message: 'Name is required'
              },
              {
                pattern: /^[\da-z]([\da-z\\.\-_]*[\da-z]+)*$/,
                message: 'Image name should contain only lowercase letters, digits, separators (-, ., _) and should not start or end with a separator'
              },
              {
                validator: (rule, value, callback) => this.validateGroupName(value, callback)
              }
            ],
            initialValue: `${this.props.toolGroup ? this.props.toolGroup.name : ''}`
          })(
          <Input
            disabled={!!this.props.toolGroup}
            ref={!this.props.toolGroup ? this.initializeNameInput : null}
            onPressEnter={this.handleSubmit} />
        )}
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        key="tool group description"
        className="edit-tool-group-form-description-container"
        {...this.formItemLayout} label="Description">
        {getFieldDecorator('description',
          {
            initialValue: `${this.props.toolGroup ? this.props.toolGroup.description || '' : ''}`
          })(
          <Input
            type="textarea"
            ref={this.props.toolGroup ? this.initializeNameInput : null}
            disabled={this.props.pending} />
        )}
      </Form.Item>
    ));
    return formItems;
  };

  onSectionChange = (key) => {
    this.setState({activeTab: key});
  };

  render () {
    const isNewToolGroup = this.props.toolGroup === undefined || this.props.toolGroup === null;
    const {resetFields} = this.props.form;
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
      resetFields();
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
        footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form className="edit-tool-group-form">
            <Tabs
              size="small"
              activeKey={this.state.activeTab}
              onChange={this.onSectionChange}>
              <Tabs.TabPane key="info" tab="Info">
                {this.renderForm()}
              </Tabs.TabPane>
              {
                this.props.toolGroup && this.props.toolGroup.id && roleModel.isOwner(this.props.toolGroup) &&
                <Tabs.TabPane key="permissions" tab="Permissions">
                  <PermissionsForm
                    objectIdentifier={this.props.toolGroup.id}
                    objectType="TOOL_GROUP" />
                </Tabs.TabPane>
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
