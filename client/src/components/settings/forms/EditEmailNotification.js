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
import {observer} from 'mobx-react';
import {computed, makeObservable} from 'mobx';
import classNames from 'classnames';
import {
  message,
  Button,
  Checkbox,
  Col,
  Form,
  Input,
  Row,
  Select,
  Space
} from 'antd';
import {EditOutlined, PictureOutlined} from '@ant-design/icons';
import CodeEditor from '../../special/CodeEditor';
import compareArrays from '../../../utils/compareArrays';
import EmailPreview from './EmailPreview';
import NotificationPreferences from './edit-email-notification-preferences';
import PreferencesUpdate from '../../../models/preferences/PreferencesUpdate';
import styles from './EditEmailNotification.css';

const statuses = [
  'SUCCESS',
  'FAILURE',
  'RUNNING',
  'STOPPED',
  'PAUSING',
  'PAUSED',
  'RESUMING'
];

@observer
export default class EditEmailNotification extends React.Component {
  static propTypes = {
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    template: PropTypes.shape({
      id: PropTypes.number,
      type: PropTypes.string,
      keepInformedAdmins: PropTypes.bool,
      keepInformedOwner: PropTypes.bool,
      threshold: PropTypes.number,
      resendDelay: PropTypes.number,
      informedUserIds: PropTypes.object,
      subject: PropTypes.string,
      body: PropTypes.string,
      enabled: PropTypes.bool
    }),
    users: PropTypes.array
  };

  formRef = React.createRef();

  formItemLayout = {
    labelCol: {
      xs: {span: 24},
      sm: {span: 4},
      md: {span: 5},
      lg: {span: 5},
      xl: {span: 7}
    },
    wrapperCol: {
      xs: {span: 24},
      sm: {span: 16},
      md: {span: 15},
      lg: {span: 15},
      xl: {span: 10}
    }
  };

  state = {
    previewMode: false,
    preferences: {
      values: [],
      modified: false
    },
    preferencesSession: 0
  };

  constructor (props) {
    super(props);
    makeObservable(this, {
      modified: computed
    });
  }

  get modified () {
    if (!this.props.template) {
      return false;
    }
    const form = this.formRef.current;
    if (!form) return false;
    const checkPropModified = (prop, defaultValue) => {
      return (this.props.template[prop] || defaultValue) !== (form.getFieldValue(prop) || defaultValue);
    };
    const checkIntPropModified = (prop) => {
      return +this.props.template[prop] !== +form.getFieldValue(prop);
    };
    const checkArrayPropModified = (prop, comparerFn = ((a, b) => a === b)) => {
      return !compareArrays((this.props.template[prop] || []).map(i => i),
        form.getFieldValue(prop), comparerFn);
    };
    return this.state.preferences.modified ||
      checkPropModified('enabled') ||
      checkPropModified('keepInformedAdmins') ||
      checkPropModified('keepInformedOwner') ||
      checkPropModified('subject', '') ||
      checkPropModified('body', '') ||
      checkIntPropModified('threshold') ||
      checkIntPropModified('resendDelay') ||
      checkArrayPropModified('informedUserIds', (a, b) => +a === +b) ||
      checkArrayPropModified('statusesToInform');
  }

  handleSubmit = (e) => {
    e.preventDefault();
    this.formRef.current.validateFields()
      .then(async (values) => {
        let {
          threshold,
          ...restValues
        } = values;
        if (this.props.template.type === 'LONG_PAUSED_STOPPED') {
          threshold = 1;
        }
        const payload = {
          threshold,
          ...restValues
        };
        this.props.onSubmit && this.props.onSubmit(payload);
        if (this.state.preferences.modified) {
          const request = new PreferencesUpdate();
          await request.send(this.state.preferences.values);
          if (request.error) {
            message.error(request.error, 5);
          }
        }
        this.setState({
          preferencesSession: this.state.preferencesSession + 1
        });
      })
      .catch(() => {});
  };

  bodyValueChanged = (code) => {
    this.formRef.current && this.formRef.current.setFieldsValue({body: code});
  };

  preferencesChanged = (preferences, modified) => {
    this.setState({
      preferences: {
        values: preferences,
        modified
      }
    });
  };

  setPreviewMode = (preview) => {
    this.setState({
      previewMode: preview
    });
  };

  get initialValues () {
    const t = this.props.template;
    if (!t) return {};
    return {
      enabled: t.enabled,
      keepInformedAdmins: t.keepInformedAdmins,
      keepInformedOwner: t.keepInformedOwner,
      informedUserIds: (t.informedUserIds || []).map(u => `${u}`),
      statusesToInform: (t.statusesToInform || []).map(s => s),
      threshold: t.threshold,
      resendDelay: t.resendDelay,
      subject: t.subject,
      body: t.body
    };
  }

  render () {
    if (!this.props.template) {
      return null;
    }
    const renderThresholdAndDelay = this.props.template.type === 'LONG_INIT' ||
      this.props.template.type === 'LONG_RUNNING' ||
      this.props.template.type === 'LONG_STATUS' ||
      this.props.template.type === 'LONG_PAUSED';
    const renderDelay = ['IDLE_RUN', 'FULL_NODE_POOL'].includes(this.props.template.type);
    const renderStatusesToInform = this.props.template.type === 'PIPELINE_RUN_STATUS';
    const form = this.formRef.current;
    return (
      <div style={{width: '100%', overflowY: 'auto'}}>
        <NotificationPreferences
          type={this.props.template.type}
          session={this.state.preferencesSession}
          onChange={this.preferencesChanged}
        />
        <Form
          key={this.props.template.id}
          ref={this.formRef}
          className="edit-email-notification-form"
          layout="horizontal"
          initialValues={this.initialValues}
        >
          <Form.Item
            style={{marginBottom: 0}}
            className="edit-email-notification-enabled-container"
            name="enabled"
            valuePropName="checked"
          >
            <Checkbox>Enabled</Checkbox>
          </Form.Item>
          <Form.Item
            style={{marginBottom: 0}}
            className="edit-email-notification-keep-informed-admins-container"
            name="keepInformedAdmins"
            valuePropName="checked"
          >
            <Checkbox>Keep admins informed</Checkbox>
          </Form.Item>
          <Form.Item
            style={{marginBottom: 0}}
            className="edit-email-notification-keep-informed-owners-container"
            name="keepInformedOwner"
            valuePropName="checked"
          >
            <Checkbox>Keep owners informed</Checkbox>
          </Form.Item>
          <Form.Item
            style={{marginBottom: 0}}
            label="Informed users"
            className="edit-email-notification-keep-informed-users-container"
            name="informedUserIds"
          >
            <Select
              size="small"
              filterOption={
                (input, option) =>
                  option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
              mode="tags">
              {
                this.props.users.map(u => {
                  return (
                    <Select.Option key={u.id} value={`${u.id}`}>
                      {u.userName}
                    </Select.Option>
                  );
                })
              }
            </Select>
          </Form.Item>
          <Form.Item
            style={{
              marginBottom: 0,
              display: renderStatusesToInform ? 'inherit' : 'none'
            }}
            label="Statuses to inform:"
            className="edit-email-notification-statuses-to-inform-container"
            name="statusesToInform"
          >
            <Select
              size="small"
              filterOption={
                (input, option) =>
                  option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0}
              mode="tags">
              {
                statuses.map(s => {
                  return (
                    <Select.Option key={s} value={s}>
                      {s}
                    </Select.Option>
                  );
                })
              }
            </Select>
          </Form.Item>
          <Form.Item
            style={{
              marginBottom: 0,
              display: renderThresholdAndDelay ? 'inherit' : 'none'
            }}
            label="Threshold (sec)"
            className="edit-email-notification-threshold-container"
            name="threshold"
            rules={[
              {
                validator: (rule, value) => {
                  if (!isNaN(value) && (typeof value === 'number' || (value && value.length > 0))) {
                    if (+value <= 0 && +value !== -1) {
                      return Promise.reject(new Error('Only positive number or -1 is allowed'));
                    }
                  } else {
                    return Promise.reject(new Error('Please enter a valid number'));
                  }
                  return Promise.resolve();
                }
              }
            ]}
          >
            <Input size="small" />
          </Form.Item>
          <Form.Item
            style={{
              marginBottom: 0,
              display: renderThresholdAndDelay || renderDelay ? 'inherit' : 'none'
            }}
            label="Resend delay (sec)"
            className="edit-email-notification-threshold-container"
            name="resendDelay"
            rules={[
              {
                validator: (rule, value) => {
                  if (!isNaN(value)) {
                    if (+value <= 0 && +value !== -1) {
                      return Promise.reject(new Error('Only positive number or -1 is allowed'));
                    }
                  } else {
                    return Promise.reject(new Error('Please enter a valid number'));
                  }
                  return Promise.resolve();
                }
              }
            ]}
          >
            <Input size="small" />
          </Form.Item>
          <Row type="flex" style={{marginTop: 5}}>
            <Space.Compact size="small">
              <Button
                type={this.state.previewMode ? 'default' : 'primary'}
                style={{width: 80}}
                onClick={() => this.setPreviewMode(false)}>
                <EditOutlined />Edit
              </Button>
              <Button
                type={!this.state.previewMode ? 'default' : 'primary'}
                style={{width: 80}}
                onClick={() => this.setPreviewMode(true)}>
                <PictureOutlined />Preview
              </Button>
            </Space.Compact>
          </Row>
          <Form.Item
            style={{
              marginBottom: 0,
              display: this.state.previewMode ? 'none' : 'inherit'
            }}
            label="Subject"
            className="edit-email-notification-subject-container"
            name="subject"
            rules={[
              {
                required: true,
                message: 'Subject is required'
              }
            ]}
          >
            <Input />
          </Form.Item>
          <Row type="flex" align="middle" style={{display: this.state.previewMode ? 'flex' : 'none'}}>
            <Col style={{marginBottom: 5}}>Subject:</Col>
            <Col style={{flex: 1}}>
              <EmailPreview
                style={{
                  marginLeft: 5
                }}
                iFrameStyle={{
                  height: 34,
                  width: '100%',
                  overflow: 'hidden',
                  border: 'none'
                }}
                value={form ? form.getFieldValue('subject') : undefined} />
            </Col>
          </Row>
          <Form.Item
            style={{
              marginBottom: 0,
              display: this.state.previewMode ? 'none' : 'inherit'
            }}
            label="Body"
            className="edit-email-notification-body-container"
            name="body"
            rules={[
              {
                required: true,
                message: 'Body is required'
              }
            ]}
          >
            <Input style={{display: 'none'}} />
          </Form.Item>
          <CodeEditor
            ref={editor => { this.editor = editor; }}
            className={classNames(styles.codeEditor, 'cp-code-editor')}
            language="application/x-jsp"
            onChange={this.bodyValueChanged}
            lineWrapping
            defaultCode={this.props.template.body}
          />
          <Row style={{display: this.state.previewMode ? 'flex' : 'none'}}>
            <EmailPreview
              className={classNames(styles.codeEditor, 'cp-code-editor')}
              style={{
                lineHeight: 'inherit',
                backgroundColor: 'transparent',
                overflow: 'hidden',
                width: '100%'
              }}
              value={form ? form.getFieldValue('body') : undefined} />
          </Row>
        </Form>
        <Row className={styles.actions} type="flex" justify="end">
          <Button
            id="edit-email-notification-form-cancel-button"
            disabled={!this.modified}
            size="small"
            onClick={() => {
              this.formRef.current && this.formRef.current.resetFields();
              this.setState({
                preferencesSession: this.state.preferencesSession + 1
              });
            }}>Revert</Button>
          <Button
            id="edit-email-notification-form-ok-button"
            disabled={!this.modified}
            type="primary"
            size="small"
            onClick={this.handleSubmit}>Save</Button>
        </Row>
      </div>
    );
  }

  emailNotificationChanged = () => {
    this.editor && this.editor.reset();
    this.setState({
      previewMode: false
    });
  };

  resetFormFields = (props) => {
    props = props || this.props;
    if (this.formRef.current) {
      this.formRef.current.resetFields();
    }
    this.editor && this.editor.setValue(props.template ? props.template.body || '' : '');
    this.setState({
      preferencesSession: this.state.preferencesSession + 1
    });
  };

  componentDidUpdate (prevProps) {
    if (!prevProps.template || !this.props.template || prevProps.template.id !== this.props.template.id) {
      this.resetFormFields(this.props);
    }
  }

  componentWillReceiveProps (nextProps) {
    if (!nextProps.template || !this.props.template || nextProps.template.id !== this.props.template.id) {
      this.emailNotificationChanged();
      this.resetFormFields(nextProps);
    }
  }
}
