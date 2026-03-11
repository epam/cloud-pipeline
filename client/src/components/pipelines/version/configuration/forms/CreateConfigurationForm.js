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
import {Button, Form, Modal, Input, Row, Spin, Select} from 'antd';

export default class CreateConfigurationForm extends React.Component {
  formRef = React.createRef();

  static propTypes = {
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    configurations: PropTypes.array,
    defaultTemplate: PropTypes.string
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
    this.formRef.current.validateFields()
      .then((values) => {
        this.props.onSubmit(values);
      })
      .catch(() => {});
  };

  render () {
    const modalFooter = this.props.pending ? false : (
      <Row type="flex" justify="end">
        <Button
          disabled={this.props.pending}
          id="create-pipeline-configuration-form-cancel-button"
          onClick={this.props.onCancel}>CANCEL</Button>
        <Button
          disabled={this.props.pending}
          id="create-pipeline-configuration-form-create-button"
          type="primary"
          htmlType="submit"
          onClick={this.handleSubmit}>CREATE</Button>
      </Row>
    );
    const description = this.props.pipeline && this.props.pipeline.description
      ? this.props.pipeline.description
      : '';
    return (
      <Modal
        mask={{closable: !this.props.pending}}
        afterClose={() => this.formRef.current && this.formRef.current.resetFields()}
        closable={!this.props.pending}
        open={this.props.visible}
        title="Create configuration"
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form
            ref={this.formRef}
            className="edit-pipeline-form"
            initialValues={{
              name: undefined,
              description,
              template: this.props.defaultTemplate
            }}
          >
            <Form.Item
              className="create-pipeline-configuration-name-container"
              {...this.formItemLayout}
              label="Configuration name"
              name="name"
              rules={[
                {required: true, message: 'Configuration name is required'},
                {
                  pattern: /^[\da-zA-Z._\-@ ]+$/,
                  message: 'Name can contain only letters, digits, spaces, \'_\', \'-\', \'@\' and \'.\'.'
                }
              ]}
            >
              <Input
                ref={this.initializeNameInput}
                onPressEnter={this.handleSubmit}
                disabled={this.props.pending}
              />
            </Form.Item>
            <Form.Item
              className="create-pipeline-configuration-description-container"
              {...this.formItemLayout}
              label="Description"
              name="description"
            >
              <Input.TextArea
                autoSize={{minRows: 2, maxRows: 6}}
                disabled={this.props.pending}
              />
            </Form.Item>
            <Form.Item
              className="create-pipeline-configuration-template-container"
              {...this.formItemLayout}
              label="Template"
              name="template"
            >
              <Select>
                {this.props.configurations.map(c => {
                  return (
                    <Select.Option key={c.name} value={c.name}>
                      {c.name}
                    </Select.Option>
                  );
                })}
              </Select>
            </Form.Item>
          </Form>
        </Spin>
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

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible) {
      this.focusNameInput();
    }
  }
}
