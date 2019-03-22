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

import React, {Component} from 'react';
import {Button, Form, Input, Modal, Row, Spin} from 'antd';
import {inject} from 'mobx-react';

@Form.create()
@inject('visible', 'onCancel', 'onSubmit', 'title', 'pending')
export default class RegisterVersionFormDialog extends Component {

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 4}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 20}
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

  render () {
    const {getFieldDecorator} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Button
          id="register-version-form-cancel-button"
          onClick={this.props.onCancel}>Cancel</Button>
        <Button
          id="register-version-form-release-button"
          type="primary" htmlType="submit" onClick={this.handleSubmit}>RELEASE</Button>
      </Row>
    );
    return (
      <Modal
        maskClosable={!this.props.pending}
        closable={!this.props.pending}
        visible={this.props.visible}
        title={this.props.title}
        onCancel={this.props.onCancel} footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form className="register-version-form">
            <Form.Item
              className="register-version-form-version-container"
              {...this.formItemLayout} label="Version">
              {
                getFieldDecorator('version',
                  {
                    rules: [
                      {required: true, message: 'Version name is required'}
                    ]
                  })(
                    <Input
                      ref={this.initializeNameInput}
                      onPressEnter={this.handleSubmit}
                      disabled={this.props.pending} />
              )}
            </Form.Item>
            <Form.Item
              className="register-version-form-description-container"
              {...this.formItemLayout} label="Description">
              {getFieldDecorator('description', {})(
                <Input disabled={this.props.pending} type="textarea" rows={4} />
              )}
            </Form.Item>
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
