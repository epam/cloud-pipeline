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
import {Button, Modal, Form, Input, Row, Spin} from 'antd';
import PropTypes from 'prop-types';

@Form.create()
export default class SaveFilterForm extends React.Component {

  static propTypes = {
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    name: PropTypes.string
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
        this.props.onSubmit(values);
      }
    });
  };

  render () {
    const {getFieldDecorator, resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row>
        <Button
          id="filter-edit-form-cancel-button"
          onClick={this.props.onCancel}>Cancel</Button>
        <Button
          id="filter-edit-form-ok-button"
          type="primary"
          htmlType="submit"
          onClick={this.handleSubmit}>SAVE</Button>
      </Row>
    );
    const onClose = () => {
      resetFields();
    };
    return (
      <Modal
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title="Save filter"
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form className="filter-edit-form">
            <Form.Item
              className="filter-edit-form-name-container"
              {...this.formItemLayout} label="Name">
              {getFieldDecorator('name', {
                rules: [
                  {required: true, message: 'Name is required'}
                ],
                initialValue: this.props.name
              })(
                <Input
                  ref={this.initializeNameInput}
                  onPressEnter={this.handleSubmit}
                  disabled={this.props.pending} />
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
