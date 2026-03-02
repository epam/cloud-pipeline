/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {
  observer} from 'mobx-react';
import {observable, computed, makeObservable} from 'mobx';
import {
  Button,
  Input,
  Form,
  Row,
  Col,
  Checkbox
} from 'antd';
import {EditOutlined, PictureOutlined} from '@ant-design/icons';
import UsersRolesSelect from '../../../../../special/users-roles-select';
import CodeEditor from '../../../../../special/CodeEditor';
import EmailPreview from '../../../../../../components/settings/forms/EmailPreview';
import NotificationTemplates from '../../../../../../models/settings/NotificationTemplates';
import styles from './life-cycle-forms.css';

const columnLayout = {
  labelCol: {
    xs: {span: 24},
    sm: {span: 10}
  },
  wrapperCol: {
    xs: {span: 24},
    sm: {span: 10}
  }
};
const fullWidthLayout = {
  labelCol: {
    xs: {span: 24},
    sm: {span: 5}
  },
  wrapperCol: {
    xs: {span: 24},
    sm: {span: 17}
  }
};

const TEMPLATE_KEY = 'DATASTORAGE_LIFECYCLE_ACTION';

@observer
class NotificationForm extends React.Component {
  state = {
    previewMode: false,
    pending: false
  };
  systemTemplate;

  notifyFormContainer;

  constructor (props) {
    super(props);
    makeObservable(this, {
      systemTemplate: observable,
      initialBody: computed,
      initialSubject: computed
    });
  }

  componentDidMount () {
    this.fetchEmailSettings();
  }

  componentDidUpdate (prevProps) {
    if (this.props.notificationsDisabled !== prevProps.notificationsDisabled) {
      this.checkRequiredFields();
    }
    if (this.props.rule.id !== prevProps.rule.id) {
      this.fetchEmailSettings();
    }
  }

  get initialBody () {
    const {rule} = this.props;
    if (rule.notification && rule.notification.body) {
      return rule.notification.body;
    }
    if (this.systemTemplate) {
      return this.systemTemplate.body;
    }
    return undefined;
  }

  get initialSubject () {
    const {rule} = this.props;
    if (rule.notification && rule.notification.subject) {
      return rule.notification.subject;
    }
    if (this.systemTemplate) {
      return this.systemTemplate.subject;
    }
    return undefined;
  }

  get notifyUsers () {
    const {form} = this.props;
    return form && form.getFieldValue('notification.notifyUsers');
  }

  fetchEmailSettings = () => {
    this.setState({pending: true}, async () => {
      const request = new NotificationTemplates();
      await request.fetch();
      this.setState({pending: false}, () => {
        if (!request.error) {
          this.systemTemplate = (request.value || [])
            .find(template => template.name === TEMPLATE_KEY);
        }
      });
    });
  };

  checkRequiredFields = () => {
    setTimeout(() => {
      const {form} = this.props;
      if (form) {
        form.validateFields([
          'notification.recipients',
          'notification.body',
          'notification.subject'
        ]).catch(() => {});
      }
    });
  };

  setPreviewMode = (preview) => {
    this.setState({previewMode: preview});
  };

  onChangeUseDefaultNotify = event => {
    const {onChangeUseDefaultNotify} = this.props;
    onChangeUseDefaultNotify && onChangeUseDefaultNotify(event.target.checked);
  };

  renderNotificationTemplate = () => {
    const {
      form,
      notificationsDisabled,
      useDefaultNotify
    } = this.props;
    const {previewMode, pending} = this.state;
    if (useDefaultNotify) {
      return null;
    }
    return (
      <div>
        <Row
          type="flex"
          justify="start"
          style={{marginBottom: 5}}
        >
          <Col offset={3}>
            <Button.Group size="small">
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
            </Button.Group>
          </Col>
        </Row>
        <Row>
          <Form.Item
            {...fullWidthLayout}
            className={styles.formItem}
            label="Subject"
            style={{display: previewMode ? 'none' : 'inherit'}}
            name={['notification', 'subject']}
            rules={[{
              required: !notificationsDisabled,
              message: ' '
            }]}
          >
            <Input
              disabled={notificationsDisabled || pending}
            />
          </Form.Item>
          <Row
            style={{
              display: previewMode ? 'flex' : 'none',
              marginBottom: 5
            }}
            type="flex"
            align="middle"
          >
            <Col
              {...fullWidthLayout.labelCol}
              style={{
                padding: '0px 10px 3px 0px',
                textAlign: 'right'
              }}
            >
              Subject:
            </Col>
            <Col {...fullWidthLayout.wrapperCol}>
              <EmailPreview
                iFrameStyle={{
                  height: 34,
                  width: '100%',
                  overflow: 'hidden',
                  border: 'transparent'
                }}
                value={form ? form.getFieldValue(['notification', 'subject']) : undefined}
              />
            </Col>
          </Row>
        </Row>
        <Row>
          <Form.Item
            {...fullWidthLayout}
            className={styles.formItem}
            label="Notification"
            style={{display: previewMode ? 'none' : 'inherit'}}
            name={['notification', 'body']}
            valuePropName="code"
            rules={[{
              required: !notificationsDisabled,
              message: ' '
            }]}
          >
            <CodeEditor
              className={classNames(
                styles.codeEditor,
                'cp-code-editor'
              )}
              language="application/x-jsp"
              lineWrapping
              readOnly={notificationsDisabled || pending}
            />
          </Form.Item>
          <div style={{display: previewMode ? 'flex' : 'none'}}>
            <Col offset={3} style={{width: '100%'}}>
              <EmailPreview
                className={classNames(
                  styles.codeEditor,
                  'cp-code-editor',
                  'cp-bordered'
                )}
                style={{
                  lineHeight: 'inherit',
                  backgroundColor: 'transparent',
                  overflow: 'hidden',
                  width: '100%',
                  borderRadius: 4
                }}
                value={form ? form.getFieldValue(['notification', 'body']) : undefined}
              />
            </Col>
            <Col offset={2} />
          </div>
        </Row>
      </div>
    );
  };

  render () {
    const {
      notificationsDisabled,
      useDefaultNotify
    } = this.props;
    const {pending} = this.state;
    return (
      <div
        style={{width: '100%'}}
        ref={(el) => { this.notifyFormContainer = el; }}
        className={styles.notificationsForm}
      >
        <Row>
          <Form.Item
            className={styles.formItem}
            style={{marginLeft: 10}}
            name={['notification', 'disabled']}
            valuePropName="checked"
          >
            <Checkbox
              disabled={pending}
            >
              Disable all notifications for the current rule
            </Checkbox>
          </Form.Item>
        </Row>
        <Row>
          <Form.Item
            {...fullWidthLayout}
            className={styles.formItem}
            label="Recipients"
            name={['notification', 'recipients']}
            rules={[{
              required: !notificationsDisabled && !this.notifyUsers,
              message: ' '
            }]}
          >
            <UsersRolesSelect
              disabled={notificationsDisabled || pending}
              style={{flex: 1}}
              dropdownStyle={{maxHeight: '80%'}}
              popupContainerFn={() => this.notifyFormContainer}
              onChange={this.checkRequiredFields}
            />
          </Form.Item>
        </Row>
        <Row>
          <Form.Item
            {...fullWidthLayout}
            className={styles.formItem}
            label=" "
            colon={false}
            name={['notification', 'notifyUsers']}
            valuePropName="checked"
          >
            <Checkbox
              disabled={notificationsDisabled || pending}
              onChange={this.checkRequiredFields}
            >
              Storage users
            </Checkbox>
          </Form.Item>
        </Row>
        <Row
          type="flex"
          justify="space-between"
        >
          <Col style={{width: '50%'}}>
            <Form.Item
              {...columnLayout}
              className={styles.formItem}
              label="Notice period (days)"
              name={['notification', 'notifyBeforeDays']}
            >
              <Input
                disabled={notificationsDisabled || pending}
              />
            </Form.Item>
          </Col>
          <Col style={{width: '50%'}}>
            <Form.Item
              {...columnLayout}
              labelCol={{sm: {span: 8}}}
              wrapperCol={{sm: {span: 12}}}
              className={styles.formItem}
              label="Prolongation period (days)"
              name={['notification', 'prolongDays']}
            >
              <Input
                disabled={notificationsDisabled || pending}
              />
            </Form.Item>
          </Col>
        </Row>
        <Row>
          <Checkbox
            checked={useDefaultNotify}
            onChange={this.onChangeUseDefaultNotify}
            className={styles.formItem}
            style={{marginLeft: 10}}
            disabled={notificationsDisabled || pending}
          >
            Use default template
          </Checkbox>
        </Row>
        {this.renderNotificationTemplate()}
      </div>
    );
  }
}

NotificationForm.propTypes = {
  form: PropTypes.object,
  rule: PropTypes.object,
  notificationsDisabled: PropTypes.bool,
  useDefaultNotify: PropTypes.bool,
  onChangeUseDefaultNotify: PropTypes.func
};

export default NotificationForm;
