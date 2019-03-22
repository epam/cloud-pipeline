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
import {Button, Modal, Form, Input, Row, Spin} from 'antd';

@Form.create()
export default class EnableToolForm extends React.Component {

  static propTypes = {
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    imagePrefix: PropTypes.string
  };

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 3}
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
        this.props.onSubmit(values);
      }
    });
  };

  renderForm = () => {
    const {getFieldDecorator} = this.props.form;
    const formItems = [];
    formItems.push((
      <Form.Item
        key="tool name"
        className="enable-tool-form-image-container"
        {...this.formItemLayout} label="Image">
        {getFieldDecorator('image',
          {
            rules: [{required: true, message: 'Image is required'}]
          })(
            <Input
              style={{width: '100%'}}
              ref={this.initializeNameInput}
              onPressEnter={this.handleSubmit}
              addonBefore={this.props.imagePrefix} />
        )}
      </Form.Item>
    ));
    return formItems;
  };

  render () {
    const {resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row type="flex" justify="space-between">
        <Button
          disabled={this.props.pending}
          id="enable-tool-form-cancel-button"
          onClick={this.props.onCancel}>CANCEL</Button>
        <Button
          disabled={this.props.pending}
          id="enable-tool-form-enable-button"
          type="primary" htmlType="submit"
          onClick={this.handleSubmit}>ENABLE</Button>
      </Row>
    );
    const onClose = () => {
      resetFields();
    };
    return (
      <Modal
        width="50%"
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title="Enable tool"
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form className="enable-tool-form">
            {this.renderForm()}
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
