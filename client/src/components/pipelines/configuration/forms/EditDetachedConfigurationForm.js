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
import {Button, Modal, Form, Input, Row, Col, Spin, Tabs} from 'antd';
import PermissionsForm from '../../../roleModel/PermissionsForm';
import roleModel from '../../../../utils/roleModel';

@roleModel.authenticationInfo
@Form.create()
export default class EditDetachedConfigurationForm extends React.Component {

  state = {
    activeTab: 'info',
    deleteDialogVisible: false
  };

  static propTypes = {
    configuration: PropTypes.shape({
      id: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      name: PropTypes.string,
      description: PropTypes.string,
      mask: PropTypes.number,
      locked: PropTypes.bool
    }),
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    onDelete: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 8}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 16}
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

  renderForm = () => {
    const {getFieldDecorator} = this.props.form;
    const formItems = [];
    formItems.push((
      <Form.Item
        key="configuration name"
        className="edit-configuration-form-name-container"
        {...this.formItemLayout} label="Configuration name">
        {getFieldDecorator('name',
          {
            rules: [{required: true, message: 'Configuration name is required'}],
            initialValue: `${this.props.configuration ? this.props.configuration.name : ''}`
          })(
          <Input
            disabled={this.props.pending}
            ref={this.initializeNameInput}
            onPressEnter={this.handleSubmit} />
        )}
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        key="configuration description"
        className="edit-configuration-form-description-container"
        {...this.formItemLayout} label="Configuration description">
        {getFieldDecorator('description',
          {
            initialValue: `${this.props.configuration && this.props.configuration.description
              ? this.props.configuration.description : ''}`
          })(
          <Input
            type="textarea"
            autosize={{minRows: 2, maxRows: 6}}
            disabled={this.props.pending} />
        )}
      </Form.Item>
    ));
    return formItems;
  };

  openDeleteDialog = () => {
    this.setState({deleteDialogVisible: true});
  };

  closeDeleteDialog = () => {
    this.setState({deleteDialogVisible: false});
  };

  onDeleteClicked = () => {
    this.closeDeleteDialog();
    if (this.props.onDelete) {
      this.props.onDelete();
    }
  };

  getDeleteModalFooter = () => {
    return (
      <Row type="flex" justify="space-between">
        <Col span={12}>
          <Row type="flex" justify="start">
            {
              roleModel.manager.configuration(
                <Button
                  id="edit-configuration-delete-dialog-cancel-button"
                  onClick={this.closeDeleteDialog}>Cancel</Button>
              )
            }
          </Row>
        </Col>
        <Col span={12}>
          <Row type="flex" justify="end">
            <Button
              id="edit-configuration-delete-dialog-delete-button"
              type="danger"
              onClick={() => this.onDeleteClicked()}>Delete</Button>
          </Row>
        </Col>
      </Row>
    );
  };

  onSectionChange = (key) => {
    this.setState({activeTab: key});
  };

  render () {
    const isNewConfiguration = this.props.configuration === undefined || this.props.configuration === null;
    const isReadOnly = this.props.configuration ? this.props.configuration.locked : false;
    const handleDelete = this.props.onDelete;
    const {resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row type="flex" justify={!isNewConfiguration && handleDelete ? 'space-between' : 'end'}>
        {!isNewConfiguration && handleDelete &&
        <Button
          disabled={this.props.pending}
          id="edit-configuration-form-delete-button"
          type="danger"
          onClick={this.openDeleteDialog}>DELETE</Button>}
        <div>
          <Button
            disabled={this.props.pending}
            id="edit-configuration-form-cancel-button"
            onClick={this.props.onCancel}>CANCEL</Button>
          <Button
            disabled={this.props.pending}
            id={`edit-configuration-form-${isNewConfiguration ? 'create' : 'save'}-button`}
            type="primary" htmlType="submit"
            onClick={this.handleSubmit}>{isNewConfiguration ? 'CREATE' : 'SAVE'}</Button>
        </div>
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
          isNewConfiguration
            ? (
            this.props.pipelineTemplate
              ? `Create configuration (${this.props.pipelineTemplate.id})`
              : 'Create configuration'
          )
            : 'Edit configuration info'
        }
        onCancel={this.props.onCancel}
        footer={this.state.activeTab === 'info' ? modalFooter : false}>
        <Spin spinning={this.props.pending}>
          <Form className="edit-configuration-form">
            <Tabs
              size="small"
              activeKey={this.state.activeTab}
              onChange={this.onSectionChange}>
              <Tabs.TabPane key="info" tab="Info">
                {this.renderForm()}
              </Tabs.TabPane>
              {
                this.props.configuration &&
                this.props.configuration.id &&
                roleModel.isOwner(this.props.configuration) ?
                  (
                    <Tabs.TabPane key="permissions" tab="Permissions">
                      <PermissionsForm
                        readonly={isReadOnly}
                        objectIdentifier={this.props.configuration.id}
                        objectType="configuration" />
                    </Tabs.TabPane>
                  ) : undefined
              }
            </Tabs>
          </Form>
        </Spin>
        <Modal
          onCancel={this.closeDeleteDialog}
          visible={this.state.deleteDialogVisible}
          title="Are you sure you want to delete configuration?"
          footer={this.getDeleteModalFooter()}>
          <p>This operation cannot be undone.</p>
        </Modal>
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
