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
import connect from '../../../../utils/connect';
import {computed} from 'mobx';
import PropTypes from 'prop-types';
import dockerRegistries from '../../../../models/tools/DockerRegistriesTree';
import {Modal, Form, Row, Col, Spin, Checkbox, Alert} from 'antd';
import roleModel from '../../../../utils/roleModel';
import localization from '../../../../utils/localization';
import LoadToolTags from '../../../../models/tools/LoadToolTags';
import CommitRunDockerImageInput from './CommitRunDockerImageInput';
import {PIPELINE_RUN_COMMIT_CHECK_FAILED} from '../../../../models/pipelines/PipelineRunCommitCheck';

@Form.create()
@connect({
  dockerRegistries
})
@localization.localizedComponent
@inject(({dockerRegistries}) => {
  return {
    docker: dockerRegistries
  };
})
@observer
export default class CommitRunForm extends localization.LocalizedReactComponent {

  static propTypes = {
    onInitialized: PropTypes.func,
    onPressEnter: PropTypes.func,
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    commitCheck: PropTypes.bool,
    defaultDockerImage: PropTypes.string,
    deleteRuntimeFiles: PropTypes.bool,
    stopPipeline: PropTypes.bool,
    displayDeleteRuntimeFilesSelector: PropTypes.bool,
    displayStopPipelineSelector: PropTypes.bool
  };

  static defaultProps = {
    displayDeleteRuntimeFilesSelector: true,
    displayStopPipelineSelector: true
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

  state = {
    toolValid: true
  };

  @computed
  get toolValid () {
    return this.state.toolValid;
  }

  validate = async () => {
    return new Promise((resolve) => {
      this.props.form.validateFieldsAndScroll((err, values) => {
        if (!err) {
          (async () => {
            const {newTool, newVersion, isLink} = await this.checkDockerImage(values.newImageName);
            if (isLink) {
              Modal.error({
                title: `You cannot push to linked tool ${values.newImageName}`
              });
              resolve(null);
              return;
            }
            const doCommit = () => {
              const registryPath = values.newImageName.split('/')[0];
              const [registry] = this.registries.filter(r => r.path === registryPath);
              values.registryToCommitId = registry.id;
              resolve(values);
            };
            if (newTool || newVersion) {
              doCommit();
            } else {
              Modal.confirm({
                title: `${values.newImageName} already exists. Overwrite?`,
                style: {
                  wordWrap: 'break-word'
                },
                onOk: () => doCommit(),
                onCancel: () => resolve(null),
                okText: 'OK',
                cancelText: 'CANCEL'
              });
            }
          })();
        } else {
          resolve(null);
        }
      });
    });
  };

  onToolValidation = (valid) => {
    this.setState({
      toolValid: valid
    });
  };

  checkDockerImage = async (imageName) => {
    const [registryPath, groupName, imagePart] = imageName.split('/');
    let [image, version] = (imagePart || '').split(':');
    version = version || 'latest';
    const [registry] = this.registries.filter(r => r.path === registryPath);
    if (registry) {
      const [group] = (registry.groups || []).filter(g => g.name === groupName);
      if (group) {
        const toolImageName = `${groupName}/${image}`;
        const [tool] = (group.tools || []).filter(t => t.image === toolImageName);
        if (tool) {
          if (tool.link) {
            return {isLink: true};
          }
          const tags = new LoadToolTags(tool.id);
          await tags.fetch();
          if (!tags.error) {
            return {
              newVersion: (tags.value || []).indexOf(version) === -1
            };
          }
        }
      }
    }
    return {newTool: true};
  };

  get registries () {
    if (!this.props.docker.loaded) {
      return [];
    }
    return (this.props.docker.value.registries || []).map(r => r);
  }

  get canCommitIntoRegistry () {
    const selectFirstWritableGroup = g =>
    roleModel.writeAllowed(g) || (g.tools || []).filter(t => roleModel.writeAllowed(t)).length > 0;
    const selectFirstWritableRegistry = r =>
      (r.groups || []).filter(selectFirstWritableGroup).length > 0;
    return this.registries.filter(selectFirstWritableRegistry).length > 0;
  }

  get dockerImage () {
    if (!this.props.docker.loaded) {
      return null;
    }
    if (this.props.defaultDockerImage) {
      const parts = this.props.defaultDockerImage.split('/');
      if (parts.length === 3) {
        let tool = parts[2];
        if (tool) {
          tool = tool.split(':')[0];
        }
        let [selectedRegistry] = this.registries.filter(r => r.path === parts[0]);
        let group = parts[1];
        if (!selectedRegistry) {
          selectedRegistry = this.registries[0];
          group = selectedRegistry && selectedRegistry.groups
            ? selectedRegistry.groups[0].name : '';
          tool = '';
        }
        if (selectedRegistry && group) {
          const [registryGroup] = (selectedRegistry.groups || []).filter(g => g.name === group);
          if (registryGroup) {
            const [groupTool] = (registryGroup.tools || [])
              .filter(t => t.image === `${group}/${tool}`);
            if (groupTool) {
              if (!roleModel.writeAllowed(groupTool) && roleModel.writeAllowed(registryGroup)) {
                tool = '';
              } else if (!roleModel.writeAllowed(groupTool) &&
                !roleModel.writeAllowed(registryGroup)) {
                tool = '';
                const selectFirstWritableGroup = g =>
                roleModel.writeAllowed(g) || (g.tools || [])
                    .filter(t => roleModel.writeAllowed(t)).length > 0;
                const selectFirstWritableRegistry = r =>
                  (r.groups || []).filter(selectFirstWritableGroup).length > 0;
                [selectedRegistry] = this.registries.filter(selectFirstWritableRegistry);
                if (selectedRegistry) {
                  const [writableGroup] = (selectedRegistry.groups || [])
                    .filter(selectFirstWritableGroup);
                  if (writableGroup) {
                    group = writableGroup.name;
                  }
                }
              }
            }
          }
        }
        if (selectedRegistry && group) {
          return `${selectedRegistry.path}/${group}/${tool}`;
        }
      }
    }
    return null;
  }

  validateNewImage = (value, callback) => {
    if (value && value.indexOf('___') >= 0) {
      callback('You cannot use more than two underscores subsequently');
    } else {
      if (value) {
        this.checkDockerImage(value)
          .then((res) => {
            const {isLink} = res;
            if (isLink) {
              callback('You cannot push to linked tool');
              return;
            }
            const parts = value.split('/');
            const registry = parts.shift();
            const group = parts.shift();
            const toolAndVersion = parts.join('/');
            if (registry === undefined || group === undefined || toolAndVersion === undefined) {
              callback('Docker image name is required');
              return;
            } else {
              const nameRegExp = /^[\da-z]([\da-z\\.\-_]*[\da-z]+)*$/;
              const toolAndVersionParts = toolAndVersion.split(':');
              const tool = toolAndVersionParts.shift();
              const version = toolAndVersionParts.join(':');
              if (!/^[\da-zA-Z.\-_:]+$/.test(registry)) {
                callback('Registry path should contain valid URL');
                return;
              } else if (!nameRegExp.test(group)) {
                callback('Tool group should contain only lowercase letters, digits, separators (-, ., _) and should not start or end with a separator');
                return;
              } else if (!nameRegExp.test(tool)) {
                callback('Image name should contain only lowercase letters, digits, separators (-, ., _) and should not start or end with a separator');
                return;
              } else if (version && !nameRegExp.test(version)) {
                callback('Version should contain only lowercase letters, digits, separators (-, ., _) and should not start or end with a separator');
                return;
              }
            }
          });
      } else {
        callback();
      }
    }
  };

  reset = () => {
    this.props.form && this.props.form.resetFields();
  };

  render () {
    const {getFieldDecorator} = this.props.form;

    return (
      <Spin spinning={this.props.pending}>
        {this.canCommitIntoRegistry ? (
          <Form className="commit-pipeline-run-form">
            {
              `${this.props.commitCheck}`.toLowerCase() === 'false' &&
              <Row>
                <Alert
                  type="error"
                  message={PIPELINE_RUN_COMMIT_CHECK_FAILED} />
                <br />
              </Row>
            }
            <Form.Item
              style={{marginBottom: 5}}
              key="Image name"
              className="commit-pipeline-run-form-image-name-container"
              {...this.formItemLayout}
              label="Docker image">
              {getFieldDecorator('newImageName',
                {
                  rules: [
                    {
                      required: true,
                      message: 'Image name is required'
                    },
                    {
                      validator: (rule, value, callback) => this.validateNewImage(value, callback)
                    }
                  ],
                  initialValue: this.dockerImage
                })(
                <CommitRunDockerImageInput
                  onValidation={this.onToolValidation}
                  visible={this.props.visible}
                  onPressEnter={this.props.onPressEnter}
                  registries={this.registries}
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            {
              this.props.displayDeleteRuntimeFilesSelector &&
              <Row type="flex" style={{height: 30}}>
                <Col xs={24} sm={6} />
                <Col xs={24} sm={18}>
                  <Form.Item
                    key="Delete files"
                    className="commit-pipeline-run-form-delete-files-container">
                    {getFieldDecorator('deleteFiles', {
                      valuePropName: 'checked',
                      initialValue: this.props.deleteRuntimeFiles
                    })(
                      <Checkbox disabled={this.props.pending}>
                        Delete runtime files
                      </Checkbox>
                    )}
                  </Form.Item>
                </Col>
              </Row>
            }
            {
              this.props.displayStopPipelineSelector &&
              <Row type="flex" style={{height: 30}}>
                <Col xs={24} sm={6} />
                <Col xs={24} sm={18}>
                  <Form.Item
                    key="Stop pipeline"
                    className="commit-pipeline-run-form-stop-pipeline-container">
                    {getFieldDecorator('stopPipeline', {
                      valuePropName: 'checked',
                      initialValue: this.props.stopPipeline
                    })(
                      <Checkbox disabled={this.props.pending}>
                        Stop {this.localizedString('pipeline')}
                      </Checkbox>
                    )}
                  </Form.Item>
                </Col>
              </Row>
            }
          </Form>) : (
            <Row>
              <Alert
                type="warning"
                message="You don't have permission to write in registries" />
              <br />
            </Row>
        )}
      </Spin>
    );
  }

  componentDidMount () {
    this.props.onInitialized && this.props.onInitialized(this);
  }
}
