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
import {Button, Modal, Form, Input, Row, Spin, Tabs} from 'antd';
import {inject} from 'mobx-react';
import PropTypes from 'prop-types';
import PermissionsForm from '../../../roleModel/PermissionsForm';
import roleModel from '../../../../utils/roleModel';

@inject('visible', 'onSubmit', 'onCancel', 'pending', 'title')
@Form.create()
export default class EditFolderForm extends React.Component {

  static propTypes = {
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    name: PropTypes.string,
    folderId: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    mask: PropTypes.number,
    locked: PropTypes.bool
  };

  state = {activeTab: 'info'};

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 6}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 18}
    },
  };

  handleSubmit = (e) => {
    e.preventDefault();
    this.props.form.validateFieldsAndScroll((err, values) => {
      if (!err) {
        this.props.onSubmit(values);
      }
    });
  };

  onSectionChange = (key) => {
    this.setState({activeTab: key});
  };

  render () {
    const {getFieldDecorator, resetFields} = this.props.form;
    const modalFooter = this.props.pending || this.state.activeTab !== 'info' ? false : (
      <Row>
        <Button
          id="folder-edit-form-cancel-button"
          onClick={this.props.onCancel}>Cancel</Button>
        <Button
          id="folder-edit-form-ok-button"
          type="primary"
          htmlType="submit"
          onClick={this.handleSubmit}>OK</Button>
      </Row>
    );
    const onClose = () => {
      resetFields();
      this.setState({activeTab: 'info'});
    };
    const isEditInfo =
      !!this.props.folderId &&
      (roleModel.isOwner({mask: this.props.mask}) || roleModel.writeAllowed({mask: this.props.mask}));
    const isEditPermissions = !!this.props.folderId && roleModel.isOwner({mask: this.props.mask});
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title={this.props.title}
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Tabs
            size="small"
            activeKey={this.state.activeTab}
            onChange={this.onSectionChange}>
            <Tabs.TabPane key="info" tab="Info">
              <Form className="folder-edit-form">
                <Form.Item
                  className="folder-edit-form-name-container"
                  {...this.formItemLayout} label="Name">
                  {getFieldDecorator('name', {
                    rules: [
                      {required: true, message: 'Name is required'}
                    ],
                    initialValue: this.props.name
                  })(
                    <Input
                      ref={this.initializeNameInput}
                      onPressEnter={this.handleSubmit}
                      disabled={this.props.pending || (!isEditInfo && !!this.props.folderId)} />
                  )}
                </Form.Item>
              </Form>
            </Tabs.TabPane>
            {
              this.props.folderId &&
              <Tabs.TabPane key="permissions" tab="Permissions">
                <PermissionsForm
                  readonly={this.props.locked || !isEditPermissions}
                  objectIdentifier={this.props.folderId}
                  objectType="FOLDER" />
              </Tabs.TabPane>
            }
          </Tabs>
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
