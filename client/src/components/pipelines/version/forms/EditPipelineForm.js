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
import localization from '../../../../utils/localization';

@roleModel.authenticationInfo
@localization.localizedComponent
@Form.create()
export default class EditPipelineForm extends localization.LocalizedReactComponent {

  state = {
    activeTab: 'info',
    deleteDialogVisible: false,
    editRepositorySettings: false
  };

  static propTypes = {
    pipeline: PropTypes.shape({
      id: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      name: PropTypes.string,
      description: PropTypes.string,
      mask: PropTypes.number,
      locked: PropTypes.bool,
      repository: PropTypes.string,
      repositoryToken: PropTypes.string
    }),
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    onDelete: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    pipelineTemplate: PropTypes.object
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
        if (!this.state.editRepositorySettings) {
          values.repository = undefined;
          values.token = undefined;
        } else {
          values.token = values.token || '';
        }
        this.props.onSubmit(values);
      }
    });
  };

  displayRepositorySettings = () => {
    this.setState({editRepositorySettings: true});
  };

  renderForm = () => {
    const {getFieldDecorator} = this.props.form;
    const formItems = [];
    formItems.push((
      <Form.Item
        key="pipeline name"
        className="edit-pipeline-form-name-container"
        {...this.formItemLayout} label={`${this.localizedString('Pipeline')} name`}>
        {getFieldDecorator('name',
          {
            rules: [{required: true, message: `${this.localizedString('Pipeline')} name is required`}],
            initialValue: `${this.props.pipeline ? this.props.pipeline.name : ''}`
          })(
            <Input
              disabled={this.props.pending}
              onPressEnter={this.handleSubmit}
              ref={this.initializeNameInput} />
        )}
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        key="pipeline description"
        className="edit-pipeline-form-description-container"
        {...this.formItemLayout} label={`${this.localizedString('Pipeline')} description`}>
        {getFieldDecorator('description',
          {
            initialValue: `${this.props.pipeline && this.props.pipeline.description
              ? this.props.pipeline.description : ''}`
          })(
            <Input
              type="textarea"
              autosize={{minRows: 2, maxRows: 6}}
              disabled={this.props.pending} />
        )}
      </Form.Item>
    ));
    if (this.state.editRepositorySettings) {
      formItems.push((
        <Form.Item
          key="repository"
          className="edit-pipeline-form-repository-container"
          {...this.formItemLayout} label="Repository">
          {getFieldDecorator('repository',
            {
              initialValue: `${this.props.pipeline && this.props.pipeline.repository ? this.props.pipeline.repository : ''}`
            })(
            <Input
              onPressEnter={this.handleSubmit}
              disabled={!!this.props.pipeline}/>
          )}
        </Form.Item>
      ));
      formItems.push((
        <Form.Item
          key="token"
          className="edit-pipeline-form-repository-container"
          {...this.formItemLayout} label="Token">
          {getFieldDecorator('token', {
            initialValue: `${this.props.pipeline && this.props.pipeline.repositoryToken ? this.props.pipeline.repositoryToken : ''}`
          })(
            <Input onPressEnter={this.handleSubmit} />
          )}
        </Form.Item>
      ));
    } else {
      formItems.push((
        <Row key="edit repository settings" style={{textAlign: 'right'}}>
          <a onClick={this.displayRepositorySettings}>Edit repository settings</a>
        </Row>
      ));
    }
    return formItems;
  };

  openDeleteDialog = () => {
    this.setState({deleteDialogVisible: true});
  };

  closeDeleteDialog = () => {
    this.setState({deleteDialogVisible: false});
  };

  onDeleteClicked = (keepRepository) => {
    this.closeDeleteDialog();
    if (this.props.onDelete) {
      this.props.onDelete(keepRepository);
    }
  };

  getDeleteModalFooter = () => {
    return (
      <Row type="flex" justify="space-between">
        <Col span={12}>
          <Row type="flex" justify="start">
            <Button
              id="edit-pipeline-delete-dialog-cancel-button"
              onClick={this.closeDeleteDialog}>Cancel</Button>
          </Row>
        </Col>
        <Col span={12}>
          <Row type="flex" justify="end">
            <Button
              id="edit-pipeline-delete-dialog-unregister-button"
              type="danger"
              onClick={() => this.onDeleteClicked(true)}>Unregister</Button>
            <Button
              id="edit-pipeline-delete-dialog-delete-button"
              type="danger"
              onClick={() => this.onDeleteClicked(false)}>Delete</Button>
          </Row>
        </Col>
      </Row>
    );
  };

  onSectionChange = (key) => {
    this.setState({activeTab: key});
  };

  render () {
    const isNewPipeline = this.props.pipeline === undefined || this.props.pipeline === null;
    const isReadOnly = this.props.pipeline ? this.props.pipeline.locked : false;
    const handleDelete = this.props.onDelete;
    const {resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row type="flex" justify={!isNewPipeline && handleDelete && roleModel.isManager.pipeline(this) ? 'space-between' : 'end'}>
        {!isNewPipeline && handleDelete &&
          roleModel.manager.pipeline(
            <Button
              disabled={this.props.pending}
              id="edit-pipeline-form-delete-button"
              type="danger"
              onClick={this.openDeleteDialog}>DELETE</Button>
          )}
        <div>
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-cancel-button"
            onClick={this.props.onCancel}>CANCEL</Button>
          <Button
            disabled={this.props.pending}
            id={`edit-pipeline-form-${isNewPipeline ? 'create' : 'save'}-button`}
            type="primary" htmlType="submit"
            onClick={this.handleSubmit}>{isNewPipeline ? 'CREATE' : 'SAVE'}</Button>
        </div>
      </Row>
    );
    const onClose = () => {
      resetFields();
      this.setState({activeTab: 'info', editRepositorySettings: false});
    };
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title={
          isNewPipeline
            ? (
              this.props.pipelineTemplate
              ? `Create ${this.localizedString('pipeline')} (${this.props.pipelineTemplate.id})`
              : `Create ${this.localizedString('pipeline')}`
            )
            : `Edit ${this.localizedString('pipeline')} info`
        }
        onCancel={this.props.onCancel}
        footer={this.state.activeTab === 'info' ? modalFooter : false}>
        <Spin spinning={this.props.pending}>
          <Form className="edit-pipeline-form">
            <Tabs
              size="small"
              activeKey={this.state.activeTab}
              onChange={this.onSectionChange}>
              <Tabs.TabPane key="info" tab="Info">
                {this.renderForm()}
              </Tabs.TabPane>
              {
                this.props.pipeline && this.props.pipeline.id && roleModel.isOwner(this.props.pipeline) &&
                <Tabs.TabPane key="permissions" tab="Permissions">
                  <PermissionsForm
                    readonly={isReadOnly}
                    objectIdentifier={this.props.pipeline.id}
                    objectType="pipeline" />
                </Tabs.TabPane>
              }
            </Tabs>
          </Form>
        </Spin>
        <Modal
          onCancel={this.closeDeleteDialog}
          visible={this.state.deleteDialogVisible}
          title={`Do you want to delete a ${this.localizedString('pipeline')} with repository or only unregister it?`}
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
