/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Form, Modal, Input, Row, Col, Spin, Select, Checkbox, Tabs} from 'antd';
import {CloseCircleOutlined, ExclamationCircleOutlined, InfoCircleOutlined} from '@ant-design/icons';
import Markdown from '../../special/markdown';
import styles from './EditSystemNotificationForm.css';

export default class EditSystemNotificationForm extends React.Component {
  formRef = React.createRef();
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
    this.formRef.current.validateFields()
      .then((values) => {
        values.state = values.state ? 'ACTIVE' : 'INACTIVE';
        this.props.onSubmit(values);
      })
      .catch(() => {});
  };

  renderForm = () => {
    const form = this.formRef.current;
    const notification = this.props.notification;
    const formItems = [];
    if (notification) {
      formItems.push((
        <Form.Item
          key="notification id"
          style={{display: 'none'}}
          className="edit-notification-form-id-container"
          {...this.formItemLayout}
          name="notificationId"
        >
          <Input disabled />
        </Form.Item>
      ));
    }
    formItems.push((
      <Form.Item
        key="notification title"
        className="edit-notification-form-title-container"
        {...this.formItemLayout}
        label="Title"
        name="title"
        rules={[{required: true, message: 'Title is required'}]}
      >
        <Input
          disabled={this.props.pending}
          onPressEnter={this.handleSubmit}
          ref={this.initializeNameInput}
        />
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        key="notification body"
        className="edit-notification-form-body-container"
        {...this.formItemLayout}
        label="Body"
        name="body"
      >
        <Tabs
          type="card"
          className="cp-tabs-no-padding"
          items={[
            {
              key: 'write',
              label: 'Write',
              children: (
                <Input
                  type="textarea"
                  autoSize={{minRows: 2, maxRows: 6}}
                  className={styles.notificationBodyInput}
                  disabled={this.props.pending}
                  placeholder="Notification text"
                />
              )
            },
            {
              key: 'preview',
              label: 'Preview',
              children: (
                <Markdown
                  className={styles.notificationPreviewContainer}
                  md={form ? form.getFieldValue('body') : ''}
                />
              )
            }
          ]}
        />
      </Form.Item>
    ));
    formItems.push((
      <Form.Item
        key="notification severity"
        className="edit-notification-form-severity-container"
        style={{marginBottom: 10}}
        {...this.formItemLayout}
        label="Severity"
        name="severity"
      >
        <Select>
          <Select.Option key="INFO" value="INFO" title="Info">
            <div className={styles.select}>
              <InfoCircleOutlined className="cp-setting-info cp-icon-large" />
              Info
            </div>
          </Select.Option>
          <Select.Option key="WARNING" value="WARNING" title="Warning">
            <div className={styles.select}>
              <ExclamationCircleOutlined className="cp-setting-warning cp-icon-large" />
              Warning
            </div>
          </Select.Option>
          <Select.Option key="CRITICAL" value="CRITICAL" title="Critical">
            <div className={styles.select}>
              <CloseCircleOutlined className="cp-setting-critical cp-icon-large" />
              Critical
            </div>
          </Select.Option>
        </Select>
      </Form.Item>
    ));
    formItems.push((
      <Row type="flex" key="notification blocking">
        <Col xs={24} sm={6} />
        <Col xs={24} sm={18}>
          <Form.Item
            className="edit-notification-form-blocking-container"
            name="blocking"
            valuePropName="checked"
          >
            <Checkbox>Blocking</Checkbox>
          </Form.Item>
        </Col>
      </Row>
    ));
    formItems.push((
      <Form.Item
        key="notification state"
        className="edit-notification-form-state-container"
        {...this.formItemLayout}
        label="State"
        name="state"
        valuePropName="checked"
      >
        <Checkbox>Active</Checkbox>
      </Form.Item>
    ));
    return formItems;
  };

  render () {
    const isNewNotification = this.props.notification === undefined || this.props.notification === null;
    const notification = this.props.notification;
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
      this.formRef.current && this.formRef.current.resetFields();
    };
    return (
      <Modal
        mask={{closable: !this.props.pending}}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        open={this.props.visible}
        title={
          isNewNotification
            ? 'Create notification'
            : 'Edit notification'
        }
        onCancel={this.props.onCancel}
        footer={modalFooter}>
        <Spin spinning={this.props.pending}>
          <Form
            ref={this.formRef}
            className="edit-notification-form"
            initialValues={{
              notificationId: notification ? `${notification.notificationId}` : '',
              title: notification ? notification.title : '',
              body: notification && notification.body ? notification.body : '',
              severity: notification && notification.severity ? notification.severity : 'INFO',
              blocking: notification && notification.state ? notification.blocking : false,
              state: notification && notification.state ? notification.state === 'ACTIVE' : false
            }}
          >
            {this.renderForm()}
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
