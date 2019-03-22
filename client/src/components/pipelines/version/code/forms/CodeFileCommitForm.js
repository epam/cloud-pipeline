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
import {Button, Modal, Form, Input, Row, Col, Spin, Select, Checkbox} from 'antd';
import connect from '../../../../../utils/connect';
import localization from '../../../../../utils/localization';
import pipelines from '../../../../../models/pipelines/Pipelines';

@connect({
  pipelines
})
@localization.localizedComponent
@Form.create()
export default class CodeFileCommitForm extends localization.LocalizedReactComponent {

  static propTypes = {
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    configurations: PropTypes.object,
    configChangedWarning: PropTypes.string,
    configChanged: PropTypes.bool
  };

  state = {updateConfig: false};

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
        if (!this.props.configChanged) {
          values.configName = undefined;
        } else {
          values.updateConfig = this.state.updateConfig;
        }
        this.props.onSubmit(values);
      }
    });
  };

  getConfigurationsList = () => {
    if (!this.props.configurations || this.props.configurations.pending || this.props.configurations.error) {
      return [];
    }
    return this.props.configurations.value.map(c => c);
  };

  getDefaultConfigurationName = () => {
    const list = this.getConfigurationsList();
    if (list.length > 0) {
      const [defaultConfiguration] = list.filter(c => c.default);
      if (defaultConfiguration) {
        return defaultConfiguration.name;
      }
    }
    return undefined;
  };

  renderChangeConfigFormItems = () => {
    const {getFieldDecorator} = this.props.form;

    const onUpdateConfigChanged = (e) => {
      this.setState({updateConfig: e.target.checked});
    };

    let configurations;
    if (this.props.configurations && !this.props.configurations.pending && !this.props.configurations.error) {
      configurations = this.props.configurations.value.map(c => c.name);
    } else {
      configurations = [];
    }

    if (this.props.configChanged) {
      return [
        <Row
          type="flex"
          justify="center"
          key="config warning">
          {this.props.configChangedWarning || `Configuration changed for ${this.localizedString('pipeline')}`}
        </Row>,
        <Row
          key="update config">
          <Col xs={24} sm={6} />
          <Col xs={24} sm={18}>
            <Form.Item className="code-file-commit-form-update-config-container">
              <Checkbox value={this.state.updateConfig} onChange={onUpdateConfigChanged}>Update configuration</Checkbox>
            </Form.Item>
          </Col>
        </Row>,
        this.state.updateConfig ? (<Form.Item
          key="configurations"
          className="code-file-commit-form-configurations-container"
          {...this.formItemLayout}
          label="Configuration">
          {
            getFieldDecorator('configName',
              {
                initialValue: this.getDefaultConfigurationName()
              })(
              <Select>
                {configurations.map(c => {
                  return (
                    <Select.Option key={c} value={c}>
                      {c}
                    </Select.Option>
                  );
                })}
              </Select>
            )}
        </Form.Item>) : undefined
      ];
    }
    return undefined;
  };

  render () {
    const {getFieldDecorator, resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Button onClick={this.props.onCancel}>Cancel</Button>
        <Button type="primary" htmlType="submit" onClick={this.handleSubmit}>Commit</Button>
      </Row>
    );
    const onClose = () => {
      resetFields();
    };
    return (
      <Modal maskClosable={!this.props.pending}
             afterClose={() => onClose()}
             closable={!this.props.pending}
             visible={this.props.visible}
             title="Commit"
             onCancel={this.props.onCancel}
             footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form>
            <Form.Item {...this.formItemLayout} label="Commit message">
              {getFieldDecorator('message', {rules: [{required: true, message: 'Commit message is required'}]})(
                <Input
                  type="textarea"
                  ref={this.initializeNameInput}
                  onPressEnter={this.handleSubmit}
                  disabled={this.props.pending} />
              )}
            </Form.Item>
            {
              this.renderChangeConfigFormItems()
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
