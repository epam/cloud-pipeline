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
import {observer} from 'mobx-react';
import {observable} from 'mobx';
import PropTypes from 'prop-types';
import {
  Form,
  Input
} from 'antd';
import CodeEditorFormItem from '../../../../../special/CodeEditorFormItem';
import {WDLItemPortsFormItem, validatePorts} from './form-items/WDLItemPortsFormItem';
import {PortTypes} from './utilities';
import {
  parseRawDockerImageValue,
  WDLRuntimeDockerFormItem
} from './form-items/WDLRuntimeDockerFormItem';
import {reservedRegExp} from './utilities/reserved';
import styles from './WDLItemProperties.css';

export function prepareTask (task) {
  if (task && task.type === 'task') {
    let command = task.command;
    let docker;
    if (command) {
      const parts = command.split('\n');
      if (parts[0].trim().toLowerCase().startsWith('task_script=') &&
        parts.length >= 5 &&
        parts[1].trim().toLowerCase().replace(/ /g, '') === 'cat>"$task_script"<<eol' &&
        parts[parts.length - 2].trim().toLowerCase() === 'eol') {
        const lastLineParts = parts[parts.length - 1].match(/[^\s"]+|"([^"]*)"/g);
        if (lastLineParts.length > 4) {
          docker = parseRawDockerImageValue(lastLineParts[lastLineParts.length - 5].trim());
          let newCommand = parts[2];
          for (let i = 3; i < parts.length - 2; i++) {
            newCommand += '\n';
            newCommand += parts[i];
          }
          command = newCommand;
        }
      }
    }
    if (task.runtime && task.runtime.docker) {
      docker = parseRawDockerImageValue(task.runtime.docker);
    }
    return {
      ...task,
      command,
      runtime: {
        docker
      }
    };
  }
  return task;
}

function getAllAliases ({children, type, name}) {
  let result = [];
  if (type === 'workflow') {
    result.push(name);
  }
  if (children) {
    result = [...result, ...Object.keys(children)];
    Object.values(children).forEach(child => {
      result = [...result, ...getAllAliases(child)];
    });
  }
  return result;
}

@observer
export class WDLItemProperties extends React.Component {
  static propTypes = {
    task: PropTypes.shape({
      name: PropTypes.string,
      alias: PropTypes.string,
      command: PropTypes.string,
      runtime: PropTypes.object,
      inputs: PropTypes.object,
      outputs: PropTypes.object
    }),
    workflow: PropTypes.object,
    type: PropTypes.string,
    onInitialize: PropTypes.func,
    onChange: PropTypes.func,
    pending: PropTypes.bool,
    readOnly: PropTypes.bool
  };

  formItemLayout = {
    labelCol: {span: 24},
    wrapperCol: {span: 24}
  };

  @observable tools;

  @observable command;
  @observable _connectionsModified;
  @observable _dockerAndCommandModified;

  state = {
    inputs: [],
    outputs: [],
    useDocker: false,
    docker: null
  };

  @observable inputPortsComponent;
  @observable outputPortsComponent;
  @observable dockerImageComponent;

  initializeInputPortsComponent = (component) => {
    this.inputPortsComponent = component;
  };

  initializeOutputPortsComponent = (component) => {
    this.outputPortsComponent = component;
  };

  initializeDockerImageComponent = (component) => {
    this.dockerImageComponent = component;
  };

  unInitializeInputPortsComponent = () => {
    this.inputPortsComponent = undefined;
  };

  unInitializeOutputPortsComponent = () => {
    this.outputPortsComponent = undefined;
  };

  unInitializeDockerImageComponent = () => {
    this.dockerImageComponent = undefined;
  };

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.task !== this.props.task) {
      this.inputPortsComponent && this.inputPortsComponent.reset();
      this.outputPortsComponent && this.outputPortsComponent.reset();
      this.dockerImageComponent && this.dockerImageComponent.reset();
      nextProps.form.resetFields();
    }
  }

  get isScatter () {
    return this.props.type && this.props.type === 'scatter';
  }

  get isTask () {
    return !this.props.type || this.props.type === 'task';
  }

  revertForm = () => {
    this.props.form.resetFields();
  };

  portsValidation = (rule, value, callback) => {
    let portType = PortTypes.input;
    let oppositeField = 'outputs';
    if (rule.field === 'outputs') {
      portType = PortTypes.output;
      oppositeField = 'inputs';
    }
    const selfPorts = Object.keys(value || {}).map(k => ({...value[k], name: k}));
    const selfError = validatePorts(selfPorts, portType);
    if (selfError) {
      callback(selfError);
      return;
    }
    const oppositePorts = Object.keys(this.props.form.getFieldValue(oppositeField) || {})
      .map(k => ({...value[k], name: k}));
    const ports = [...selfPorts, ...oppositePorts].map(p => p.name);
    const filterUnique = (item, index, array) => array.indexOf(item) === index;
    const uniqueNamesCount = ports.filter(filterUnique).length;
    if (ports.length !== uniqueNamesCount) {
      if (!this.props.form.isFieldValidating(oppositeField)) {
        this.props.form.validateFields([oppositeField], {force: true},
          () => callback('All variable names should be unique'));
      } else {
        callback('All variable names should be unique');
      }
      return;
    }
    if (!this.props.form.isFieldValidating(oppositeField)) {
      this.props.form.validateFields([oppositeField], {force: true}, () => callback());
    } else {
      callback();
    }
  };

  render () {
    return (
      <div style={{width: '100%', padding: 2}}>
        <Form>
          <Form.Item
            key="edit-wdl-form-name-container"
            label="Name"
            style={{marginBottom: 5}}
            className={`edit-wdl-form-name-container ${styles.hiddenItem}`}>
            {this.props.form.getFieldDecorator('name',
              {
                rules: [
                  {required: true, message: 'Name is required'},
                ],
                initialValue: `${this.props.task ? this.props.task.name : ''}`
              })(
              <Input
                disabled />
            )}
          </Form.Item>
          <Form.Item
            hasFeedback
            {...this.formItemLayout}
            key="edit-wdl-form-alias-container"
            label="Alias"
            style={{marginBottom: 5}}
            className={`edit-wdl-form-alias-container ${this.isScatter ? styles.hiddenItem: ''}`}>
            {this.props.form.getFieldDecorator('alias',
              {
                rules: [
                  {required: true, message: 'Alias is required'},
                  {
                    validator: (rule, value, callback) => {
                      if (/^[a-zA-Z][a-zA-Z0-9_]*$/.test(value) && !reservedRegExp.exec(value)) {
                        if (this.props.workflow && getAllAliases(this.props.workflow).indexOf(value) >= 0) {
                          callback('Alias should be unique');
                        } else {
                          callback();
                        }
                      } else {
                        callback('Wrong alias');
                      }
                    }
                  }
                ],
                initialValue: `${this.props.task ? this.props.task.alias : ''}`
              })(
              <Input
                disabled={this.props.pending || this.props.readOnly} />
            )}
          </Form.Item>
          <Form.Item
            key="edit-wdl-form-inputs-container"
            style={{marginBottom: 5}}
            className="edit-wdl-form-inputs-container">
            {this.props.form.getFieldDecorator('inputs',
              {
                initialValue: this.props.task ? this.props.task.inputs : {},
                rules: [{
                  validator: this.portsValidation
                }]
              })(
              <WDLItemPortsFormItem
                onInitialize={this.initializeInputPortsComponent}
                onUnMount={this.unInitializeInputPortsComponent}
                disabled={this.props.readOnly}
                portType={PortTypes.input} />
            )}
          </Form.Item>
          <Form.Item
            key="edit-wdl-form-outputs-container"
            style={{marginBottom: 5}}
            className={
              this.isScatter
                ? `edit-wdl-form-outputs-container ${styles.hiddenItem}`
                : 'edit-wdl-form-outputs-container'
            }>
            {this.props.form.getFieldDecorator('outputs',
              {
                initialValue: this.props.task ? this.props.task.outputs : {},
                rules: [{
                  validator: this.portsValidation
                }]
              })(
              <WDLItemPortsFormItem
                onInitialize={this.initializeOutputPortsComponent}
                onUnMount={this.unInitializeOutputPortsComponent}
                disabled={this.props.readOnly}
                portType={PortTypes.output} />
            )}
          </Form.Item>
          <Form.Item
            key="edit-wdl-form-docker-container"
            style={{marginBottom: 5}}
            className={
              this.isTask
                ? 'edit-wdl-form-docker-container'
                : `edit-wdl-form-docker-container ${styles.hiddenItem}`
            }>
            {this.props.form.getFieldDecorator('runtime.docker',
              {
                initialValue: this.props.task && this.props.task.runtime
                  ? this.props.task.runtime.docker
                  : undefined
              })(
              <WDLRuntimeDockerFormItem
                onInitialize={this.initializeDockerImageComponent}
                onUnMount={this.unInitializeDockerImageComponent}
                disabled={this.props.readOnly} />
            )}
          </Form.Item>
          <Form.Item
            key="edit-wdl-form-command-container"
            style={{marginBottom: 5}}
            className={
              this.isTask
                ? 'edit-wdl-form-command-container'
                : `edit-wdl-form-command-container ${styles.hiddenItem}`
            }>
            {this.props.form.getFieldDecorator('command',
              {
                initialValue: this.props.task
                  ? this.props.task.command
                  : undefined
              })(
              <CodeEditorFormItem
                ref={this.initializeEditor}
                editorClassName={`${styles.codeEditor} edit-wdl-form-code-container`}
                editorLanguage="shell"
                editorLineWrapping
                disabled={this.props.readOnly} />
            )}
          </Form.Item>
        </Form>
      </div>
    );
  }
}
