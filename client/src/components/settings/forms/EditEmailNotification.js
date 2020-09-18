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
import {computed} from 'mobx';
import {
  message,
  Button,
  Checkbox,
  Col,
  Form,
  Icon,
  Input,
  Row,
  Select
} from 'antd';
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

@Form.create()
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

  @computed
  get modified () {
    if (!this.props.template) {
      return false;
    }
    const checkPropModified = (prop, defaultValue) => {
      return (this.props.template[prop] || defaultValue) !== (this.props.form.getFieldValue(prop) || defaultValue);
    };
    const checkIntPropModified = (prop) => {
      return +this.props.template[prop] !== +this.props.form.getFieldValue(prop);
    };
    const checkArrayPropModified = (prop, comparerFn = ((a, b) => a === b)) => {
      return !compareArrays((this.props.template[prop] || []).map(i => i),
        this.props.form.getFieldValue(prop), comparerFn);
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
    this.props.form.validateFieldsAndScroll(async (err, values) => {
      if (!err) {
        this.props.onSubmit && this.props.onSubmit(values);
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
      }
    });
  };

  bodyValueChanged = (code) => {
    this.props.form.setFieldsValue({body: code});
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

  render () {
    if (!this.props.template) {
      return null;
    }
    const renderThresholdAndDelay = this.props.template.type === 'LONG_INIT' ||
      this.props.template.type === 'LONG_RUNNING' ||
      this.props.template.type === 'LONG_STATUS' ||
      this.props.template.type === 'LONG_PAUSED' ||
      this.props.template.type === 'LONG_PAUSED_STOPPED';
    const renderStatusesToInform = this.props.template.type === 'PIPELINE_RUN_STATUS';
    const {getFieldDecorator, resetFields} = this.props.form;
    return (
      <div style={{width: '100%', overflowY: 'auto'}}>
        <NotificationPreferences
          type={this.props.template.type}
          session={this.state.preferencesSession}
          onChange={this.preferencesChanged}
        />
        <Form className="edit-email-notification-form" layout="horizontal">
          <Form.Item
            style={{marginBottom: 0}}
            className="edit-email-notification-enabled-container">
            {getFieldDecorator('enabled', {
              valuePropName: 'checked',
              initialValue: this.props.template.enabled
            })(
              <Checkbox>Enabled</Checkbox>
            )}
          </Form.Item>
          <Form.Item
            style={{marginBottom: 0}}
            className="edit-email-notification-keep-informed-admins-container">
            {getFieldDecorator('keepInformedAdmins', {
              valuePropName: 'checked',
              initialValue: this.props.template.keepInformedAdmins
            })(
              <Checkbox>Keep admins informed</Checkbox>
            )}
          </Form.Item>
          <Form.Item
            style={{marginBottom: 0}}
            className="edit-email-notification-keep-informed-owners-container">
            {getFieldDecorator('keepInformedOwner', {
              valuePropName: 'checked',
              initialValue: this.props.template.keepInformedOwner
            })(
              <Checkbox>Keep owners informed</Checkbox>
            )}
          </Form.Item>
          <Form.Item
            style={{marginBottom: 0}}
            label="Informed users"
            className="edit-email-notification-keep-informed-users-container">
            {getFieldDecorator('informedUserIds', {
              initialValue: (this.props.template.informedUserIds || []).map(u => `${u}`)
            })(
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
            )}
          </Form.Item>
          <Form.Item
            style={{
              marginBottom: 0,
              display: renderStatusesToInform ? 'inherit' : 'none'
            }}
            label="Statuses to inform:"
            className="edit-email-notification-statuses-to-inform-container">
            {getFieldDecorator('statusesToInform', {
              initialValue: (this.props.template.statusesToInform || []).map(s => s)
            })(
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
            )}
          </Form.Item>
          <Form.Item
            style={{
              marginBottom: 0,
              display: renderThresholdAndDelay ? 'inherit' : 'none'
            }}
            label="Threshold (sec)"
            className="edit-email-notification-threshold-container">
            {getFieldDecorator('threshold', {
              rules: [
                {
                  validator: (rule, value, callback) => {
                    if (!isNaN(value) && (typeof value === 'number' || (value && value.length > 0))) {
                      if (+value <= 0 && +value !== -1) {
                        callback('Only positive number or -1 is allowed');
                        return;
                      }
                    } else {
                      callback('Please enter a valid number');
                    }
                    callback();
                  }
                }
              ],
              initialValue: this.props.template.threshold
            })(
              <Input size="small" />
            )}
          </Form.Item>
          <Form.Item
            style={{
              marginBottom: 0,
              display: renderThresholdAndDelay ? 'inherit' : 'none'
            }}
            label="Resend delay (sec)"
            className="edit-email-notification-threshold-container">
            {getFieldDecorator('resendDelay', {
              rules: [
                {
                  validator: (rule, value, callback) => {
                    if (!isNaN(value)) {
                      if (+value <= 0 && +value !== -1) {
                        callback('Only positive number or -1 is allowed');
                        return;
                      }
                    } else {
                      callback('Please enter a valid number');
                    }
                    callback();
                  }
                }
              ],
              initialValue: this.props.template.resendDelay
            })(
              <Input size="small" />
            )}
          </Form.Item>
          <Row type="flex" style={{marginTop: 5}}>
            <Button.Group size="small">
              <Button
                type={this.state.previewMode ? 'default' : 'primary'}
                style={{width: 80}}
                onClick={() => this.setPreviewMode(false)}>
                <Icon type="edit" />Edit
              </Button>
              <Button
                type={!this.state.previewMode ? 'default' : 'primary'}
                style={{width: 80}}
                onClick={() => this.setPreviewMode(true)}>
                <Icon type="picture" />Preview
              </Button>
            </Button.Group>
          </Row>
          <Form.Item
            style={{
              marginBottom: 0,
              display: this.state.previewMode ? 'none' : 'inherit'
            }}
            label="Subject"
            className="edit-email-notification-subject-container">
            {getFieldDecorator('subject', {
              rules: [
                {
                  required: true,
                  message: 'Subject is required'
                }
              ],
              initialValue: this.props.template.subject
            })(
              <Input />
            )}
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
                value={this.props.form.getFieldValue('subject')} />
            </Col>
          </Row>
          <Form.Item
            style={{
              marginBottom: 0,
              display: this.state.previewMode ? 'none' : 'inherit'
            }}
            label="Body"
            className="edit-email-notification-body-container">
            {getFieldDecorator('body', {
              rules: [
                {
                  required: true,
                  message: 'Body is required'
                }
              ],
              initialValue: this.props.template.body
            })(
              <Input style={{display: 'none'}} />
            )}
            <CodeEditor
              ref={editor => this.editor = editor}
              className={styles.codeEditor}
              language="application/x-jsp"
              onChange={this.bodyValueChanged}
              lineWrapping
              defaultCode={this.props.template.body}
            />
          </Form.Item>
          <Row style={{display: this.state.previewMode ? 'flex' : 'none'}}>
            <EmailPreview
              className={styles.codeEditor}
              style={{
                lineHeight: 'inherit',
                backgroundColor: 'transparent',
                overflow: 'hidden',
                width: '100%'
              }}
              value={this.props.form.getFieldValue('body')} />
          </Row>
        </Form>
        <Row className={styles.actions} type="flex" justify="end">
          <Button
            id="edit-email-notification-form-cancel-button"
            disabled={!this.modified}
            size="small"
            onClick={() => {
              resetFields();
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
    props.form && props.form.resetFields();
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
