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
import connect from '../../../../../../utils/connect';
import {observable, computed} from 'mobx';
import PropTypes from 'prop-types';
import {
  AutoComplete,
  Button,
  Modal,
  Form,
  Input,
  Row,
  Col,
  Spin,
  Table,
  Icon,
  Checkbox
} from 'antd';
import dockerRegistries from '../../../../../../models/tools/DockerRegistriesTree';
import CodeEditor from '../../../../../special/CodeEditor';
import DockerImageInput from '../../../../launch/form/DockerImageInput';
import styles from './EditWDLToolForm.css';

@Form.create()
@connect({
  dockerRegistries
})
@inject(({dockerRegistries}) => {
  return {
    dockerRegistries
  };
})
@observer
export default class EditWDLToolForm extends React.Component {
  static propTypes = {
    task: PropTypes.shape({
      name: PropTypes.string,
      alias: PropTypes.string,
      command: PropTypes.string,
      runtime: PropTypes.object,
      inputs: PropTypes.object,
      outputs: PropTypes.object
    }),
    type: PropTypes.string,
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 3}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 21}
    }
  };

  @observable tools;

  state = {
    inputs: [],
    outputs: [],
    useDocker: false,
    docker: null
  };

  primitiveTypes = [
    'String',
    'File',
    'Int',
    'Boolean',
    'Float',
    'Object',
    'ScatterItem'
  ];

  getNameInputStyle = (item) => {
    if (item.nameError) {
      return {
        border: '1px solid red'
      };
    }
    return {};
  };

  getValueInputStyle = (item) => {
    if (item.valueError) {
      return {
        border: '1px solid red'
      };
    }
    return {};
  };

  getTypeInputStyle = (item) => {
    if (item.typeError) {
      return {
        border: '1px solid red',
        borderRadius: 5,
        minWidth: 100
      };
    }
    return {minWidth: 100};
  };

  variablesColumns = (isInput) => [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name, item) => (
        <Input
          style={this.getNameInputStyle(item)}
          defaultValue={name}
          onChange={this.editVariable(isInput, 'name', item.index)} />
      )
    },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      render: (type, item) => (
        <AutoComplete
          style={this.getTypeInputStyle(item)}
          dataSource={this.primitiveTypes}
          defaultValue={type}
          onChange={this.editVariableType(isInput, item.index)}
          filterOption={(input, option) => option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0} />
      )
    },
    {
      title: isInput ? 'Value (optional)' : 'Value',
      dataIndex: 'value',
      key: 'value',
      render: (value, item) => (
        <Input
          style={this.getValueInputStyle(item)}
          defaultValue={value}
          onChange={this.editVariable(isInput, 'value', item.index)} />
      )
    },
    {
      key: 'actions',
      render: (item) => {
        return (
          <a
            onClick={
              () => isInput
                ? this.removeInputVariable(item.index)
                : this.removeOutputVariable(item.index)
            }>
            <Icon type="delete" />
          </a>
        );
      }
    }
  ];

  checkInputVariables = () => {
    let error = false;
    for (let i = 0; i < (this.state.inputs || []).length; i++) {
      const variable = this.state.inputs[i];
      if (variable.removed) {
        continue;
      }
      if (!variable.name || !variable.name.length) {
        variable.nameError = true;
        error = true;
      } else {
        variable.nameError = undefined;
      }
      if (!variable.type || !variable.type.length) {
        variable.typeError = true;
        error = true;
      } else {
        variable.typeError = false;
      }
    }
    return !error;
  };

  checkOutputVariables = () => {
    let error = false;
    for (let i = 0; i < (this.state.outputs || []).length; i++) {
      const variable = this.state.outputs[i];
      if (variable.removed) {
        continue;
      }
      if (!variable.name || !variable.name.length) {
        variable.nameError = true;
        error = true;
      } else {
        variable.nameError = undefined;
      }
      if (!variable.value || !variable.value.length) {
        variable.valueError = true;
        error = true;
      } else {
        variable.valueError = undefined;
      }
      if (!variable.type || !variable.type.length) {
        variable.typeError = true;
        error = true;
      } else {
        variable.typeError = false;
      }
    }
    return !error;
  };

  handleSubmit = (e) => {
    e.preventDefault();
    const inputVariablesAreCorrect = this.checkInputVariables();
    const outputVariablesAreCorrect = this.checkOutputVariables();
    this.setState({
      inputs: this.state.inputs,
      outputs: this.state.outputs
    });
    let dockerRegistryCorrect = true;
    if (this.state.useDocker) {
      dockerRegistryCorrect = this.state.docker && this.state.docker.length;
    }
    if (inputVariablesAreCorrect && outputVariablesAreCorrect && dockerRegistryCorrect) {
      this.props.form.validateFieldsAndScroll((err, values) => {
        if (!err) {
          let command = this.command;
          let runtime = {};
          if (this.state.useDocker) {
            runtime.docker = `"${this.state.docker}"`;
          }
          values.command = command;
          values.runtime = runtime;
          values.inputs = this.state.inputs;
          values.outputs = this.state.outputs;
          this.props.onSubmit(values);
        }
      });
    }
  };

  commandEditorValueChanged = (code) => {
    this.command = code;
  };

  initializeEditor = (editor) => {
    this.editor = editor;
  };

  rebuildComponent (props) {
    this.command = props.task && props.task.command ? props.task.command : null;
    let docker = null;
    const clearQuotes = (text) => {
      text = text.trim();
      if (text.startsWith('"')) {
        text = text.substring(1);
      }
      if (text.endsWith('"')) {
        text = text.substring(0, text.length - 1);
      }
      return text;
    };
    if (this.command) {
      const parts = this.command.split('\n');
      if (parts[0].trim().toLowerCase().startsWith('task_script=') &&
        parts.length >= 5 &&
        parts[1].trim().toLowerCase().replace(/ /g, '') === 'cat>"$task_script"<<eol' &&
        parts[parts.length - 2].trim().toLowerCase() === 'eol') {
        const lastLineParts = parts[parts.length - 1].match(/[^\s"]+|"([^"]*)"/g);
        if (lastLineParts.length > 4) {
          docker = clearQuotes(lastLineParts[lastLineParts.length - 5].trim());
          let command = parts[2];
          for (let i = 3; i < parts.length - 2; i++) {
            command += '\n';
            command += parts[i];
          }
          this.command = command;
        }
      }
    }
    // "docker_image:version" || ["docker_image1:version1", "docker_image2:version1"]
    if (props.task && props.task.runtime && props.task.runtime.docker) {
      if (props.task.runtime.docker.startsWith('[') && props.task.runtime.docker.endsWith(']')) {
        docker = clearQuotes(props.task.runtime.docker.slice(1, -1).split(',').shift());
      } else {
        docker = clearQuotes(props.task.runtime.docker);
      }
    }
    const parseVariables = (vars) => {
      const result = [];
      let index = 0;
      for (let key in vars) {
        if (vars.hasOwnProperty(key)) {
          result.push({
            index: index,
            name: key,
            originalName: key,
            type: vars[key].type,
            value: vars[key].default,
            original: true
          });
          index += 1;
        }
      }
      return result;
    };
    let inputs = [];
    let outputs = [];
    if (props.task && props.task.inputs) {
      inputs = parseVariables(props.task.inputs);
    }
    if (props.task && props.task.outputs) {
      outputs = parseVariables(props.task.outputs);
    }
    this.setState({inputs, outputs, useDocker: docker !== null, docker});
    if (!props.task && this.editor) {
      this.editor.clear();
    }
  }

  componentDidMount () {
    this.rebuildComponent(this.props);
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.task !== this.props.task) {
      this.rebuildComponent(nextProps);
    }
  }

  getMaxIndex = (list) => {
    let maximum = 0;
    for (let i = 0; i < (list || []).length; i++) {
      const item = list[i];
      if (!maximum || item.index > maximum) {
        maximum = item.index;
      }
    }
    return maximum + 1;
  };

  addInputVariable = () => {
    const inputs = this.state.inputs;
    inputs.push({
      index: this.getMaxIndex(inputs),
      name: '',
      value: '',
      type: '',
      original: false,
      added: true
    });
    this.setState({inputs});
  };

  addOutputVariable = () => {
    const outputs = this.state.outputs;
    outputs.push({
      index: this.getMaxIndex(outputs),
      name: '',
      value: '',
      type: '',
      original: false,
      added: true
    });
    this.setState({outputs});
  };

  removeInputVariable = (index) => {
    const inputs = this.state.inputs;
    const [item] = inputs.filter(inputItem => inputItem.index === index);
    if (item) {
      if (!item.original) {
        const index = inputs.indexOf(item);
        if (index >= 0) {
          inputs.splice(index, 1);
        }
      } else {
        item.removed = true;
        item.edited = false;
      }
      this.setState({inputs});
    }
  };

  editVariable = (isInput, field, index) => (event) => {
    const items = isInput ? this.state.inputs : this.state.outputs;
    const [item] = items.filter(outputItem => outputItem.index === index);
    if (item) {
      item[field] = event.target.value;
      if (!item.added) {
        item.edited = true;
      }
    }
    if (isInput) {
      this.setState({inputs: items});
    } else {
      this.setState({outputs: items});
    }
  };

  editVariableType = (isInput, index) => (type) => {
    const items = isInput ? this.state.inputs : this.state.outputs;
    const [item] = items.filter(outputItem => outputItem.index === index);
    if (item) {
      item.type = type;
      if (!item.added) {
        item.edited = true;
      }
    }
    if (isInput) {
      this.setState({inputs: items});
    } else {
      this.setState({outputs: items});
    }
  };

  removeOutputVariable = (index) => {
    const outputs = this.state.outputs;
    const [item] = outputs.filter(outputItem => outputItem.index === index);
    if (item) {
      if (!item.original) {
        const index = outputs.indexOf(item);
        if (index >= 0) {
          outputs.splice(index, 1);
        }
      } else {
        item.removed = true;
      }
      this.setState({outputs});
    }
  };

  get isScatter () {
    return this.props.type && this.props.type === 'scatter';
  }

  get isTask () {
    return !this.props.type || this.props.type === 'task';
  }

  renderVariablesSections = () => {
    if (this.isScatter) {
      return [
        <Col key="inputs" xs={24} sm={24}>
          <Table
            style={{margin: 10}}
            title={() => (
              <Row>
                <Col span={12}>Input:</Col>
                <Col span={12} style={{textAlign: 'right'}}>
                  <Button id="edit-wdl-form-add-variable-button" size="small" onClick={this.addInputVariable}>ADD</Button>
                </Col>
              </Row>
            )}
            rowKey="index"
            pagination={false}
            columns={this.variablesColumns(true)}
            dataSource={this.state.inputs.filter(i => !i.removed)}
            size="small" />
        </Col>
      ];
    } else {
      return [
        <Col key="inputs" xs={24} sm={12}>
          <Table
            style={{margin: 10}}
            title={() => (
              <Row>
                <Col span={12}>Input:</Col>
                <Col span={12} style={{textAlign: 'right'}}>
                  <Button id="edit-wdl-form-add-variable-button" size="small" onClick={this.addInputVariable}>ADD</Button>
                </Col>
              </Row>
            )}
            rowKey="index"
            pagination={false}
            columns={this.variablesColumns(true)}
            dataSource={this.state.inputs.filter(i => !i.removed)}
            size="small" />
        </Col>,
        <Col key="outputs" xs={24} sm={12}>
          <Table
            style={{margin: 10}}
            title={() => (
              <Row>
                <Col span={12}>Output:</Col>
                <Col span={12} style={{textAlign: 'right'}}>
                  <Button id="edit-wdl-form-add-output-button" size="small" onClick={this.addOutputVariable}>ADD</Button>
                </Col>
              </Row>
            )}
            rowKey="index"
            pagination={false}
            columns={this.variablesColumns(false)}
            dataSource={this.state.outputs.filter(i => !i.removed)}
            size="small" />
        </Col>
      ];
    }
  };

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return (this.props.dockerRegistries.value.registries || []).map(r => r);
    }
    return [];
  }

  onUseDockerChange = (event) => {
    if (event.target.checked) {
      this.setState({useDocker: true});
    } else {
      this.setState({useDocker: false});
    }
  };

  onToolChange = (image) => {
    this.setState({docker: image});
  };

  renderDockerForm = () => {
    if (this.state.useDocker) {
      const getInputStyle = (field, borderRadius) => {
        if (!field || !field.length) {
          return {border: '1px solid red', borderRadius};
        }
        return {borderRadius};
      };
      return [
        <Row
          style={{marginTop: 10}}
          key="docker image"
          align="middle"
          type="flex">
          <Col xs={24} sm={3} style={{textAlign: 'right', paddingRight: 10}}>Docker image:</Col>
          <Col
            key="docker"
            span={21}
            style={{paddingLeft: 5, paddingRight: 5}}>
            <Spin spinning={this.props.dockerRegistries.pending}>
              <DockerImageInput
                disabled={this.props.dockerRegistries.pending}
                value={this.state.docker}
                onChange={this.onToolChange} />
            </Spin>
          </Col>
        </Row>
      ];
    }
    return undefined;
  };

  renderFormItems = () => {
    const {getFieldDecorator} = this.props.form;
    const formItems = [];

    if (this.props.type && !this.isScatter) {
      formItems.push(
        <Form.Item
          {...this.formItemLayout}
          key="edit-wdl-form-name-container"
          label="Name"
          className="edit-wdl-form-name-container">
          {getFieldDecorator('name',
            {
              rules: [{required: true, message: 'Name is required'}],
              initialValue: `${this.props.task ? this.props.task.name : ''}`
            })(
            <Input
              ref={this.props.task === undefined ? this.initializeNameInput : null}
              onPressEnter={this.handleSubmit}
              disabled={this.props.task !== undefined} />
          )}
        </Form.Item>
      );
      if (this.props.task) {
        formItems.push(
          <Form.Item
            {...this.formItemLayout}
            key="edit-wdl-form-alias-container"
            label="Alias"
            className="edit-wdl-form-alias-container">
            {getFieldDecorator('alias',
              {
                rules: [{required: true, message: 'Alias is required'}],
                initialValue: `${this.props.task ? this.props.task.alias : ''}`
              })(
              <Input
                ref={this.props.task !== undefined ? this.initializeNameInput : null}
                onPressEnter={this.handleSubmit}
                disabled={this.props.pending} />
            )}
          </Form.Item>
        );
      }
    }
    return formItems;
  };

  render () {
    const {resetFields} = this.props.form;
    const modalFooter = this.props.pending || this.props.task === null ? false : (
      <Row type="flex" justify="end">
        <Button id="edit-wdl-form-cancel-button" onClick={this.props.onCancel}>CANCEL</Button>
        <Button
          id={`edit-wdl-form-${this.props.task ? 'save' : 'add'}-button`}
          type="primary"
          htmlType="submit"
          onClick={this.handleSubmit}>
          {this.props.task ? 'SAVE' : 'ADD'}
        </Button>
      </Row>
    );
    const onClose = () => {
      if (this.editor) {
        this.editor.clear();
        this.command = '';
      }
      resetFields();
      this.setState({
        inputs: [],
        outputs: []
      });
    };
    const type = this.props.type || 'task';
    let title = `Add ${type}`;
    if (this.props.task) {
      title = `Edit ${type} ${this.props.task.name}`;
    }
    return (
      <Modal
        width="50%"
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title={title}
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form>
            {this.renderFormItems()}
            <Row style={{marginBottom: 10}}>
              {this.renderVariablesSections()}
            </Row>
            {
              this.isTask
              ? [
                <Row key="useDocker">
                  <Col
                    xs={24}
                    sm={3} />
                  <Col span={21}>
                    <Checkbox
                      className="edit-wdl-form-use-another-docker-checkbox"
                      onChange={this.onUseDockerChange}
                      checked={this.state.useDocker}>
                      Use another docker image
                    </Checkbox>
                  </Col>
                </Row>,
                this.renderDockerForm(),
                <br key="br" />,
                <Row key="code" style={{marginBottom: 10}}>
                  <Col xs={24} sm={3} style={{textAlign: 'right', paddingRight: 10}}>Command:</Col>
                  <Col xs={24} sm={21}>
                    <CodeEditor
                      ref={this.initializeEditor}
                      className={`${styles.codeEditor} edit-wdl-form-code-container`}
                      language="shell"
                      onChange={this.commandEditorValueChanged}
                      lineWrapping={true}
                      defaultCode={this.command}
                    />
                  </Col>
                </Row>
                ] : undefined
            }
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
