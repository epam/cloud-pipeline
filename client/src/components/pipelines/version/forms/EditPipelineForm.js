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
import {Button, Form, Modal, Input, Row, Col, Spin, Tabs, Select} from 'antd';
import PermissionsForm from '../../../roleModel/PermissionsForm';
import roleModel from '../../../../utils/roleModel';
import localization from '../../../../utils/localization';
import {RepositoryTypes} from '../../../special/git-repository-control';
import EnabledPath from './enabled-path';
import {getPipelineDefaultPaths} from './default-paths';
import RepositoryTypeSelector from './repository-type';

@roleModel.authenticationInfo
@localization.localizedComponent
@inject('dockerRegistries', 'pipelines', 'preferences')
@inject((stores, props) => {
  const {pipelines} = stores;
  const {pipeline} = props;
  return {
    configurations: pipeline
      ? pipelines.getConfiguration(pipeline.id, pipeline.currentVersion?.name)
      : undefined
  };
})
@observer
export default class EditPipelineForm extends localization.LocalizedReactComponent {
  formRef = React.createRef();

  state = {
    activeTab: 'info',
    deleteDialogVisible: false,
    editRepositorySettings: false
  };

  static propTypes = {
    pipeline: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      currentVersion: PropTypes.shape({
        name: PropTypes.string
      }),
      name: PropTypes.string,
      description: PropTypes.string,
      mask: PropTypes.number,
      locked: PropTypes.bool,
      repository: PropTypes.string,
      repositoryType: PropTypes.string,
      repositoryToken: PropTypes.string,
      pipelineType: PropTypes.string,
      branch: PropTypes.string,
      configurationPath: PropTypes.string,
      visibility: PropTypes.string,
      codePath: PropTypes.string,
      docsPath: PropTypes.string
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

  formItemStyle = {
    marginBottom: 5
  };

  /**
   * @returns {{docs: string, src: string}}
   */
  get pipelineDefaultPaths () {
    const {preferences, pipeline} = this.props;
    let repositoryType = RepositoryTypes.GitLab;
    if (pipeline) {
      repositoryType = pipeline.repositoryType || RepositoryTypes.GitLab;
    } else {
      try {
        const form = this.formRef.current;
        repositoryType = form ? form.getFieldValue('repositoryType') : RepositoryTypes.GitLab;
      } catch (_) {
        /* empty */
      }
    }
    return (
      getPipelineDefaultPaths(preferences)[repositoryType] || {
        src: 'src',
        docs: 'docs'
      }
    );
  }

  @computed
  get latestConfigurationsTools () {
    if (this.props.configurations && this.props.configurations.loaded) {
      return (this.props.configurations.value || [])
        .filter((c) => c.configuration && c.configuration.docker_image)
        .map((c) => c.configuration && c.configuration.docker_image);
    }
    return [];
  }

  @computed
  get tools () {
    if (
      this.props.dockerRegistries.loaded &&
      this.latestConfigurationsTools.length > 0
    ) {
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
            const imageRegExp = new RegExp(
              `^${registry.path}/${tool.image}(:.+)$`,
              'i'
            );
            if (pipelineTools.find((t) => imageRegExp.test(t))) {
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
    this.formRef.current.validateFields()
      .then((values) => {
        this.props.onSubmit(values);
      })
      .catch(() => {});
  };

  displayRepositorySettings = () => {
    this.setState({editRepositorySettings: true});
  };

  onRepositoryTypeChanged = (repositoryType) => {
    if (!this.props.pipeline && this.formRef.current) {
      const defaultPaths = getPipelineDefaultPaths(
        this.props.preferences
      )[repositoryType] || {
        src: 'src',
        docs: 'docs'
      };
      this.formRef.current.setFieldsValue({
        codePath: defaultPaths.src,
        docsPath: defaultPaths.docs
      });
      this.formRef.current.validateFields(['repository', 'token']).catch(() => {});
    }
  };

  getFormInitialValues = () => {
    const p = this.props.pipeline;
    const defaultPaths = this.pipelineDefaultPaths;
    return {
      name: p ? p.name : '',
      description: p && p.description ? p.description : '',
      visibility: p ? p.visibility : 'INHERIT',
      repositoryType: p && p.repositoryType ? p.repositoryType : RepositoryTypes.GitLab,
      repository: p && p.repository ? p.repository : '',
      branch: p && p.branch ? p.branch : undefined,
      token: p && p.repositoryToken ? p.repositoryToken : '',
      configurationPath: p && p.configurationPath ? p.configurationPath : undefined,
      codePath: p ? p.codePath : defaultPaths.src,
      docsPath: p ? p.docsPath : defaultPaths.docs
    };
  };

  renderForm = () => {
    const pipelineType = this.props.pipeline
      ? this.props.pipeline.pipelineType
      : undefined;
    const isVersionedStorage = /^versioned_storage$/i.test(pipelineType);
    const objectName = isVersionedStorage ? 'Versioned storage' : 'Pipeline';
    const readOnly = !!this.props.pipeline && !roleModel.writeAllowed(this.props.pipeline);
    const descriptionLabel = isVersionedStorage
      ? `Description:`
      : `${this.localizedString(objectName)} description`;
    const nameLabel = isVersionedStorage
      ? `Name:`
      : `${this.localizedString(objectName)} name`;
    const formItems = [];
    formItems.push(
      <Form.Item
        {...this.formItemLayout}
        style={this.formItemStyle}
        key="pipeline name"
        className="edit-pipeline-form-name-container"
        label={nameLabel}
        name="name"
        rules={[{
          required: true,
          message: `${this.localizedString(objectName)} name is required`
        }]}
      >
        <Input
          disabled={this.props.pending || readOnly}
          onPressEnter={this.handleSubmit}
          ref={this.initializeNameInput}
        />
      </Form.Item>
    );
    formItems.push(
      <Form.Item
        {...this.formItemLayout}
        style={this.formItemStyle}
        key="pipeline description"
        className="edit-pipeline-form-description-container"
        label={descriptionLabel}
        name="description"
      >
        <Input
          type="textarea"
          autoSize={{minRows: 2, maxRows: 6}}
          disabled={this.props.pending || readOnly}
        />
      </Form.Item>
    );
    if (!isVersionedStorage) {
      formItems.push(
        <Form.Item
          {...this.formItemLayout}
          style={this.formItemStyle}
          key="Visibility"
          className="edit-pipeline-form-visibility-container"
          label="Visibility"
          name="visibility"
        >
          <Select allowClear disabled={this.props.pending || readOnly}>
            <Select.Option key="INHERIT" value="INHERIT">
              Inherit
            </Select.Option>
            <Select.Option key="OWNER" value="OWNER">
              Owner
            </Select.Option>
          </Select>
        </Form.Item>
      );
      if (!this.state.editRepositorySettings) {
        formItems.push(
          <Row key="edit repository settings" style={{textAlign: 'right'}}>
            <a onClick={this.displayRepositorySettings}>
              Edit repository settings
            </a>
          </Row>
        );
      }
      formItems.push(
        <Form.Item
          key="repositoryType"
          className="edit-pipeline-form-repository-type-container"
          {...this.formItemLayout}
          style={{
            ...this.formItemStyle,
            display: this.state.editRepositorySettings ? 'inherit' : 'none'
          }}
          label="Repository Type"
          name="repositoryType"
        >
          <RepositoryTypeSelector
            disabled={!!this.props.pipeline || this.props.pending}
            onRepositoryTypeChanged={this.onRepositoryTypeChanged}
          />
        </Form.Item>
      );
      formItems.push(
        <Form.Item
          key="repository"
          className="edit-pipeline-form-repository-container"
          {...this.formItemLayout}
          style={{
            ...this.formItemStyle,
            display: this.state.editRepositorySettings ? 'inherit' : 'none'
          }}
          label="Repository"
          name="repository"
          rules={[{
            validator: (rule, value) => {
              const form = this.formRef.current;
              let repoType = form ? form.getFieldValue('repositoryType') : undefined;
              if (!repoType && this.props.pipeline) {
                repoType = this.props.pipeline.repositoryType;
              }
              if (repoType === RepositoryTypes.AzureDevOps) {
                if (!value) {
                  return Promise.reject(new Error('Repository is required'));
                }
              }
              return Promise.resolve();
            }
          }]}
        >
          <Input
            onPressEnter={this.handleSubmit}
            disabled={!!this.props.pipeline || this.props.pending}
          />
        </Form.Item>
      );
      formItems.push(
        <Form.Item
          key="branch"
          className="edit-pipeline-form-branch-container"
          {...this.formItemLayout}
          style={{
            ...this.formItemStyle,
            display: this.state.editRepositorySettings ? 'inherit' : 'none'
          }}
          label="Branch"
          name="branch"
        >
          <Input disabled={this.props.pending || readOnly} />
        </Form.Item>
      );
      formItems.push(
        <Form.Item
          key="token"
          className="edit-pipeline-form-repository-token-container"
          {...this.formItemLayout}
          style={{
            ...this.formItemStyle,
            display: this.state.editRepositorySettings ? 'inherit' : 'none'
          }}
          label="Token"
          name="token"
          rules={[{
            validator: (rule, value) => {
              const form = this.formRef.current;
              let repoType = form ? form.getFieldValue('repositoryType') : undefined;
              if (!repoType && this.props.pipeline) {
                repoType = this.props.pipeline.repositoryType;
              }
              if (repoType === RepositoryTypes.AzureDevOps) {
                if (!value) {
                  return Promise.reject(new Error('Token is required'));
                }
              }
              return Promise.resolve();
            }
          }]}
        >
          <Input
            onPressEnter={this.handleSubmit}
            type="password"
            disabled={this.props.pending || readOnly}
          />
        </Form.Item>
      );
      formItems.push(
        <Form.Item
          key="configurationPath"
          className="edit-pipeline-form-configuration-path-container"
          {...this.formItemLayout}
          style={{
            ...this.formItemStyle,
            display: this.state.editRepositorySettings ? 'inherit' : 'none'
          }}
          label="Configuration path"
          name="configurationPath"
        >
          <Input disabled={this.props.pending || readOnly} />
        </Form.Item>
      );
      formItems.push(
        <Form.Item
          key="codePath"
          className="edit-pipeline-form-code-path-container"
          {...this.formItemLayout}
          style={{
            ...this.formItemStyle,
            display: this.state.editRepositorySettings ? 'inherit' : 'none'
          }}
          label="Code path"
          name="codePath"
        >
          <EnabledPath
            disabled={this.props.pending || readOnly}
            defaultPathValue={
              this.props.pipeline && this.props.pipeline.codePath
                ? this.props.pipeline.codePath
                : this.pipelineDefaultPaths.src
            }
          />
        </Form.Item>
      );
      formItems.push(
        <Form.Item
          key="docsPath"
          className="edit-pipeline-form-docs-path-container"
          {...this.formItemLayout}
          style={{
            ...this.formItemStyle,
            display: this.state.editRepositorySettings ? 'inherit' : 'none'
          }}
          label="Docs path"
          name="docsPath"
        >
          <EnabledPath
            disabled={this.props.pending || readOnly}
            defaultPathValue={
              this.props.pipeline && this.props.pipeline.docsPath
                ? this.props.pipeline.docsPath
                : this.pipelineDefaultPaths.docs
            }
          />
        </Form.Item>
      );
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
              onClick={this.closeDeleteDialog}
            >
              Cancel
            </Button>
          </Row>
        </Col>
        <Col span={12}>
          <Row type="flex" justify="end">
            <Button
              id="edit-pipeline-delete-dialog-unregister-button"
              danger
              onClick={() => this.onDeleteClicked(true)}
            >
              Unregister
            </Button>
            <Button
              id="edit-pipeline-delete-dialog-delete-button"
              danger
              onClick={() => this.onDeleteClicked(false)}
            >
              Delete
            </Button>
          </Row>
        </Col>
      </Row>
    );
  };

  onSectionChange = (key) => {
    this.setState({activeTab: key});
  };

  getModalFooter = (isNewPipeline, isVersionedStorage) => {
    if (this.props.pending) {
      return false;
    }
    const form = this.formRef.current;
    const fieldsError = form ? (form.getFieldsError() || []) : [];
    const anyError = Array.isArray(fieldsError)
      ? fieldsError.some(f => f.errors && f.errors.length > 0)
      : false;
    const disableSubmit = this.props.pending || anyError;
    const isManager = isVersionedStorage
      ? roleModel.isManager.versionedStorage(this)
      : (roleModel.isManager.pipeline(this) || roleModel.isManager.pipelineAdmin(this));
    const deleteAllowed =
      !isNewPipeline &&
      !!this.props.onDelete &&
      roleModel.writeAllowed(this.props.pipeline) &&
      isManager;
    const saveAllowed = isNewPipeline
      ? isManager
      : roleModel.writeAllowed(this.props.pipeline);
    if (deleteAllowed && saveAllowed) {
      return (
        <Row type="flex" justify="space-between">
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-delete-button"
            danger
            onClick={this.openDeleteDialog}
          >
            DELETE
          </Button>
          <div>
            <Button
              disabled={this.props.pending}
              id="edit-pipeline-form-cancel-button"
              onClick={this.props.onCancel}
            >
              CANCEL
            </Button>
            <Button
              disabled={disableSubmit}
              id={`edit-pipeline-form-${
                isNewPipeline ? 'create' : 'save'
              }-button`}
              type="primary"
              htmlType="submit"
              onClick={this.handleSubmit}
            >
              {isNewPipeline ? 'CREATE' : 'SAVE'}
            </Button>
          </div>
        </Row>
      );
    } else if (deleteAllowed) {
      return (
        <Row type="flex" justify="space-between">
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-delete-button"
            danger
            onClick={this.openDeleteDialog}
          >
            DELETE
          </Button>
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-cancel-button"
            onClick={this.props.onCancel}
          >
            CANCEL
          </Button>
        </Row>
      );
    } else if (saveAllowed) {
      return (
        <Row type="flex" justify="end">
          <div>
            <Button
              disabled={this.props.pending}
              id="edit-pipeline-form-cancel-button"
              onClick={this.props.onCancel}
            >
              CANCEL
            </Button>
            <Button
              disabled={disableSubmit}
              id={`edit-pipeline-form-${
                isNewPipeline ? 'create' : 'save'
              }-button`}
              type="primary"
              htmlType="submit"
              onClick={this.handleSubmit}
            >
              {isNewPipeline ? 'CREATE' : 'SAVE'}
            </Button>
          </div>
        </Row>
      );
    } else {
      return (
        <Row type="flex" justify="end">
          <Button
            disabled={this.props.pending}
            id="edit-pipeline-form-cancel-button"
            onClick={this.props.onCancel}
          >
            CANCEL
          </Button>
        </Row>
      );
    }
  };

  render () {
    const isReadOnly = this.props.pipeline ? this.props.pipeline.locked : false;
    const isNewPipeline = !this.props.pipeline;
    const pipelineType = this.props.pipeline
      ? this.props.pipeline.pipelineType
      : undefined;
    const isVersionedStorage = /^versioned_storage$/i.test(pipelineType);
    const objectName = isVersionedStorage ? 'versioned storage' : 'pipeline';
    const modalFooter = this.getModalFooter(isNewPipeline, isVersionedStorage);
    const deleteConfirmTitle = (
      <span style={{paddingRight: '25px', display: 'flex'}}>
        {`Do you want to delete a ${this.localizedString(
          objectName
        )} with repository or only unregister it?`}
      </span>
    );
    const onClose = () => {
      this.formRef.current && this.formRef.current.resetFields();
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
            ? this.props.pipelineTemplate
              ? `Create ${this.localizedString(objectName)} (${
                this.props.pipelineTemplate.id
              })`
              : `Create ${this.localizedString(objectName)}`
            : `Edit ${this.localizedString(objectName)} info`
        }
        onCancel={this.props.onCancel}
        footer={this.state.activeTab === 'info' ? modalFooter : false}
      >
        <Spin spinning={this.props.pending}>
          <Form
            ref={this.formRef}
            className="edit-pipeline-form"
            initialValues={this.getFormInitialValues()}
            key={this.props.pipeline ? this.props.pipeline.id : 'new'}
          >
            <Tabs
              size="small"
              activeKey={this.state.activeTab}
              onChange={this.onSectionChange}
            >
              <Tabs.TabPane key="info" tab="Info">
                {this.renderForm()}
              </Tabs.TabPane>
              {this.props.pipeline &&
                this.props.pipeline.id &&
                roleModel.readAllowed(this.props.pipeline) && (
                <Tabs.TabPane key="permissions" tab="Permissions">
                  <PermissionsForm
                    readonly={isReadOnly || !roleModel.writeAllowed(this.props.pipeline)}
                    objectIdentifier={this.props.pipeline.id}
                    objectType="pipeline"
                    subObjectsPermissionsMaskToCheck={roleModel.buildPermissionsMask(
                      1,
                      1,
                      0,
                      0,
                      1,
                      1
                    )}
                    subObjectsToCheck={this.tools.map(
                      ({aclClass: entityClass, id: entityId, image}) => ({
                        entityId,
                        entityClass,
                        name: <b>{image}</b>
                      })
                    )}
                    subObjectsPermissionsErrorTitle={
                      <span>
                        Users shall have Read and Execute permissions for the
                        docker images, used in a current{' '}
                        {this.localizedString(objectName)}. Please review and
                        fix permissions issues below:
                      </span>
                    }
                    editOwnerAvailable={
                      roleModel.isOwner(this.props.pipeline) ||
                      roleModel.isManager.pipelineAdmin(this)
                    }
                  />
                </Tabs.TabPane>
              )}
            </Tabs>
          </Form>
        </Spin>
        <Modal
          onCancel={this.closeDeleteDialog}
          visible={this.state.deleteDialogVisible}
          title={deleteConfirmTitle}
          footer={this.getDeleteModalFooter()}
        >
          <p>This operation cannot be undone.</p>
        </Modal>
      </Modal>
    );
  }

  initializeNameInput = (input) => {
    if (input) {
      this.nameInput = input;
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

  componentDidMount () {
    const {preferences} = this.props;
    preferences.fetchIfNeededOrWait();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
  }
}
