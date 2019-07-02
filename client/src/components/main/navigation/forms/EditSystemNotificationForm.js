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
import {Button, Modal, Form, Input, Row, Col, Spin, Select, Icon, Checkbox} from 'antd';
import styles from './EditSystemNotificationForm.css';

@Form.create()
export default class EditSystemNotificationForm extends React.Component {

  static propTypes = {
    notification: PropTypes.shape({
      notificationId: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      title: PropTypes.string,
      body: PropTypes.string,
      severity: PropTypes.string,
      state: PropTypes.string
    }),
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool
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
        values.state = values.state ? 'ACTIVE' : 'INACTIVE';
        this.props.onSubmit(values);
      }
    });
  };

  renderForm = () => {
    const {getFieldDecorator} = this.props.form;
    const formItems = [];
    if (this.props.notification) {
      formItems.push((
        <Form.Item
          key="notification id"
          style={{display: 'none'}}
          className="edit-notification-form-id-container"
          {...this.formItemLayout}>
          {getFieldDecorator('notificationId',
            {
              initialValue: `${this.props.notification ? this.props.notification.notificationId : ''}`
            })(
              <Input disabled={true} />
          )}
        </Form.Item>
      ));
    }
    formItems.push((
      <Form.Item
        key="notification title"
        className="edit-notification-form-title-container"
        {...this.formItemLayout} label="Title">
        {getFieldDecorator('title',
          {
            rules: [{required: true, message: 'Title is required'}],
            initialValue: `${this.props.notification ? this.props.notification.title : ''}`
          })(
          <Input
            disabled={this.props.pending}
            onPressEnter={this.handleSubmit}
            ref={this.initializeNameInput} />
        )}
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        key="notification body"
        className="edit-notification-form-body-container"
        {...this.formItemLayout} label="Body">
        {getFieldDecorator('body',
          {
            initialValue: `${this.props.notification && this.props.notification.body
              ? this.props.notification.body : ''}`
          })(
          <Input
            type="textarea"
            autosize={{minRows: 2, maxRows: 6}}
            disabled={this.props.pending} />
        )}
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        key="notification severity"
        className="edit-notification-form-severity-container"
        style={{marginBottom: 10}}
        {...this.formItemLayout} label="Severity">
        {getFieldDecorator('severity',
          {
            initialValue: `${this.props.notification && this.props.notification.severity
              ? this.props.notification.severity : 'INFO'}`
          })(
          <Select>
            <Select.Option key="INFO" value="INFO" title="Info">
              <Icon type="info-circle-o" className={styles.info} /> Info
            </Select.Option>
            <Select.Option key="WARNING" value="WARNING" title="Warning">
              <Icon type="exclamation-circle-o" className={styles.warning} /> Warning
            </Select.Option>
            <Select.Option key="CRITICAL" value="CRITICAL" title="Critical">
              <Icon type="close-circle-o" className={styles.critical} /> Critical
            </Select.Option>
          </Select>
        )}
      </Form.Item>
    ));
    formItems.push((
      <Row type="flex" key="notification blocking">
        <Col xs={24} sm={6} />
        <Col xs={24} sm={18}>
          <Form.Item
            className="edit-notification-form-blocking-container">
            {getFieldDecorator('blocking',
              {
                valuePropName: 'checked',
                initialValue: this.props.notification && this.props.notification.state
                  ? this.props.notification.blocking : false
              })(
              <Checkbox>Blocking</Checkbox>
            )}
          </Form.Item>
        </Col>
      </Row>
    ));
    formItems.push((
      <Form.Item
        key="notification state"
        className="edit-notification-form-state-container"
        {...this.formItemLayout} label="State">
        {getFieldDecorator('state',
          {
            valuePropName: 'checked',
            initialValue: this.props.notification && this.props.notification.state
              ? this.props.notification.state === 'ACTIVE' : false
          })(
          <Checkbox>Active</Checkbox>
        )}
      </Form.Item>
    ));
    return formItems;
  };

  render () {
    const isNewNotification = this.props.notification === undefined || this.props.notification === null;
    const {resetFields} = this.props.form;
    const modalFooter = this.props.pending ? false : (
      <Row type="flex" justify="space-between">
        <Button
          disabled={this.props.pending}
          id="edit-notification-form-cancel-button"
          onClick={this.props.onCancel}>CANCEL</Button>
        <Button
          disabled={this.props.pending}
          id={`edit-notification-form-${isNewNotification ? 'create' : 'save'}-button`}
          type="primary" htmlType="submit"
          onClick={this.handleSubmit}>{isNewNotification ? 'CREATE' : 'SAVE'}</Button>
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
        title={
          isNewNotification
            ? 'Create notification'
            : 'Edit notification'
        }
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form className="edit-notification-form">
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
