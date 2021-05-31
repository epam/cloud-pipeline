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
import {Button, Modal, Form, Input, Row, Col, Spin, Tabs} from 'antd';
import PermissionsForm from '../../../roleModel/PermissionsForm';
import roleModel from '../../../../utils/roleModel';
import localization from '../../../../utils/localization';

@roleModel.authenticationInfo
@localization.localizedComponent
@inject('dockerRegistries', 'pipelines')
@inject((stores, props) => {
  const {pipelines} = stores;
  const {pipeline} = props;
  return {
    configurations: pipeline
      ? pipelines.getConfiguration(pipeline.id, pipeline.currentVersion?.name)
      : undefined
  };
})
@Form.create()
@observer
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
      currentVersion: PropTypes.shape({
        name: PropTypes.string
      }),
      name: PropTypes.string,
      description: PropTypes.string,
      mask: PropTypes.number,
      locked: PropTypes.bool,
      repository: PropTypes.string,
      repositoryToken: PropTypes.string,
      pipelineType: PropTypes.string
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

  @computed
  get latestConfigurationsTools () {
    if (this.props.configurations && this.props.configurations.loaded) {
      return (this.props.configurations.value || [])
        .filter(c => c.configuration && c.configuration.docker_image)
        .map(c => c.configuration && c.configuration.docker_image);
    }
    return [];
  }

  @computed
  get tools () {
    if (this.props.dockerRegistries.loaded && this.latestConfigurationsTools.length > 0) {
      const {registries = []} = this.props.dockerRegistries.value;
      const pipelineTools = this.latestConfigurationsTools.slice();
      const toolObjects = [];
      for (let r = 0; r < registries.length; r++) {
        const registry = registries[r];
        const {groups = []} = registry;
        for (let g = 0; g < groups.length; g++) {
          const group = groups[g];
          const {tools = []} = group;
          for (let t = 0; t < tools.length; t++) {
            const tool = tools[t];
            const imageRegExp = new RegExp(`^${registry.path}/${tool.image}(:.+)$`, 'i');
            if (pipelineTools.find(t => imageRegExp.test(t))) {
              toolObjects.push({
                ...tool,
                registry,
                group
              });
            }
          }
        }
      }
      return toolObjects;
    }
    return [];
  }

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
    const pipelineType = this.props.pipeline ? this.props.pipeline.pipelineType : undefined;
    const isVersionedStorage = /^versioned_storage$/i.test(pipelineType);
    const objectName = isVersionedStorage ? 'Versioned storage' : 'Pipeline';
    const {getFieldDecorator} = this.props.form;
    const descriptionLabel = isVersionedStorage
      ? `Description:`
      : `${this.localizedString(objectName)} description`;
    const nameLabel = isVersionedStorage
      ? `Name:`
      : `${this.localizedString(objectName)} name`;
    const formItems = [];
    formItems.push((
      <Form.Item
        {...this.formItemLayout}
        key="pipeline name"
        className="edit-pipeline-form-name-container"
        label={nameLabel}
      >
        {getFieldDecorator('name',
          {
            rules: [{required: true, message: `${this.localizedString(objectName)} name is required`}],
            initialValue: `${this.props.pipeline ? this.props.pipeline.name : ''}`
          })(
            <Input
              disabled={this.props.pending || (!!this.props.pipeline && !roleModel.writeAllowed(this.props.pipeline))}
              onPressEnter={this.handleSubmit}
              ref={this.initializeNameInput} />
        )}
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        {...this.formItemLayout}
        key="pipeline description"
        className="edit-pipeline-form-description-container"
        label={descriptionLabel}
      >
        {getFieldDecorator('description',
          {
            initialValue: `${this.props.pipeline && this.props.pipeline.description
              ? this.props.pipeline.description : ''}`
          })(
            <Input
              type="textarea"
              autosize={{minRows: 2, maxRows: 6}}
              disabled={this.props.pending || (!!this.props.pipeline && !roleModel.writeAllowed(this.props.pipeline))} />
        )}
      </Form.Item>
    ));
    if (!isVersionedStorage) {
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
                disabled={!!this.props.pipeline || this.props.pending}/>
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
              <Input
                onPressEnter={this.handleSubmit}
                disabled={this.props.pending || (!!this.props.pipeline && !roleModel.writeAllowed(this.props.pipeline))}/>
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

  getModalFooter = (isNewPipeline) => {
    if (this.props.pending) {
      return false;
    }
    const deleteAllowed = !isNewPipeline &&
      !!this.props.onDelete &&
      roleModel.writeAllowed(this.props.pipeline) &&
      roleModel.isManager.pipeline(this);
    const saveAllowed = isNewPipeline
      ? roleModel.isManager.pipeline(this)
      : roleModel.writeAllowed(this.props.pipeline);
    if (deleteAllowed && saveAllowed) {
      return (
        <Row type="flex" justify="space-between">
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-delete-button"
            type="danger"
            onClick={this.openDeleteDialog}>DELETE</Button>
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
    } else if (deleteAllowed) {
      return (
        <Row type="flex" justify="space-between">
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-delete-button"
            type="danger"
            onClick={this.openDeleteDialog}>DELETE</Button>
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-cancel-button"
            onClick={this.props.onCancel}>CANCEL</Button>
        </Row>
      );
    } else if (saveAllowed) {
      return (
        <Row type="flex" justify="end">
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
    } else {
      return (
        <Row type="flex" justify="end">
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-cancel-button"
            onClick={this.props.onCancel}>CANCEL</Button>
        </Row>
      );
    }
  };

  render () {
    const isNewPipeline = !this.props.pipeline;
    const isReadOnly = this.props.pipeline ? this.props.pipeline.locked : false;
    const pipelineType = this.props.pipeline ? this.props.pipeline.pipelineType : undefined;
    const objectName = /^versioned_storage$/i.test(pipelineType)
      ? 'versioned storage'
      : 'pipeline';
    const {resetFields} = this.props.form;
    const modalFooter = this.getModalFooter(isNewPipeline);
    const deleteConfirmTitle = (
      <span
        style={{paddingRight: '25px', display: 'flex'}}
      >
        {`Do you want to delete a ${this.localizedString(objectName)} with repository or only unregister it?`}
      </span>
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
                ? `Create ${this.localizedString(objectName)} (${this.props.pipelineTemplate.id})`
                : `Create ${this.localizedString(objectName)}`
            )
            : `Edit ${this.localizedString(objectName)} info`
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
                this.props.pipeline && this.props.pipeline.id && roleModel.readAllowed(this.props.pipeline) &&
                <Tabs.TabPane key="permissions" tab="Permissions">
                  <PermissionsForm
                    readonly={isReadOnly || !roleModel.writeAllowed(this.props.pipeline)}
                    objectIdentifier={this.props.pipeline.id}
                    objectType="pipeline"
                    subObjectsPermissionsMaskToCheck={
                      roleModel.buildPermissionsMask(1, 1, 0, 0, 1, 1)
                    }
                    subObjectsToCheck={
                      this.tools.map(({aclClass: entityClass, id: entityId, image}) => ({
                        entityId,
                        entityClass,
                        name: (<b>{image}</b>)
                      }))
                    }
                    subObjectsPermissionsErrorTitle={(
                      <span>
                        Users shall have Read and Execute permissions for the docker images,
                        used in a current {this.localizedString(objectName)}.
                        Please review and fix permissions issues below:
                      </span>
                    )}
                  />
                </Tabs.TabPane>
              }
            </Tabs>
          </Form>
        </Spin>
        <Modal
          onCancel={this.closeDeleteDialog}
          visible={this.state.deleteDialogVisible}
          title={deleteConfirmTitle}
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
