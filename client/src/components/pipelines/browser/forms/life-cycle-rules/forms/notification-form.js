/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import {
  Button,
  Input,
  Form,
  Row,
  Col,
  Icon,
  Checkbox
} from 'antd';
import UsersRolesSelect from '../../../../../special/users-roles-select';
import CodeEditor from '../../../../../special/CodeEditor';
import EmailPreview from '../../../../../../components/settings/forms/EmailPreview';
import styles from './life-cycle-forms.css';

const columnLayout = {
  labelCol: {
    xs: {span: 24},
    sm: {span: 6}
  },
  wrapperCol: {
    xs: {span: 24},
    sm: {span: 14}
  }
};
const fullWidthLayout = {
  labelCol: {
    xs: {span: 24},
    sm: {span: 3}
  },
  wrapperCol: {
    xs: {span: 24},
    sm: {span: 19}
  }
};

@inject('usersInfo')
@observer
class NotificationForm extends React.Component {
  state = {
    previewMode: false,
    pending: false
  }

  componentDidUpdate (prevProps) {
    if (this.props.notificationsDisabled !== prevProps.notificationsDisabled) {
      this.checkRequiredFields();
    }
  }

  notifyFormContainer;

  @computed
  get informedUsers () {
    const {usersInfo, rule} = this.props;
    if (usersInfo.loaded &&
      rule.notification &&
      rule.notification.informedUserIds
    ) {
      return (rule.notification.informedUserIds || []).map(id => {
        const user = (usersInfo.value || []).find(info => info.id === id);
        return user || undefined;
      }).filter(Boolean);
    }
    return [];
  }

  checkRequiredFields = () => {
    const {form} = this.props;
    form.validateFields(
      ['notification.subject'],
      {force: true}
    );
  };

  setPreviewMode = (preview) => {
    this.setState({previewMode: preview});
  };

  render () {
    const {
      form,
      rule,
      notificationsDisabled
    } = this.props;
    const {previewMode, pending} = this.state;
    const {getFieldDecorator} = form;
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
          >
            {getFieldDecorator('notification.disabled', {
              valuePropName: 'checked',
              initialValue: rule.notification && rule.notification.enabled !== undefined
                ? !rule.notification.enabled
                : false,
              rules: [{
                required: false
              }]
            })(
              <Checkbox
                disabled={pending}
              >
                Disable all notifications for the current rule
              </Checkbox>
            )}
          </Form.Item>
        </Row>
        <Row>
          <Form.Item
            {...fullWidthLayout}
            className={styles.formItem}
            label="Recipients"
          >
            {getFieldDecorator('notification.informedUserIds', {
              type: 'array',
              initialValue: rule.notification
                ? this.informedUsers
                : []
            })(
              <UsersRolesSelect
                adGroups={false}
                showRoles={false}
                disabled={notificationsDisabled || pending}
                style={{flex: 1}}
                dropdownStyle={{maxHeight: '80%'}}
                popupContainerFn={() => this.notifyFormContainer}
              />
            )}
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
            >
              {getFieldDecorator('notification.notifyBeforeDays', {
                initialValue: rule.notification && rule.notification.notifyBeforeDays
                  ? rule.notification.notifyBeforeDays
                  : undefined
              })(
                <Input
                  disabled={notificationsDisabled || pending}
                />
              )}
            </Form.Item>
          </Col>
          <Col style={{width: '50%'}}>
            <Form.Item
              {...columnLayout}
              labelCol={{sm: {span: 8}}}
              wrapperCol={{sm: {span: 12}}}
              className={styles.formItem}
              label="Prolongation period (days)"
            >
              {getFieldDecorator('notification.prolongDays', {
                initialValue: rule.notification && rule.notification.prolongDays
                  ? rule.notification.prolongDays
                  : undefined

              })(
                <Input
                  disabled={notificationsDisabled || pending}
                />
              )}
            </Form.Item>
          </Col>
        </Row>
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
                <Icon type="edit" />Edit
              </Button>
              <Button
                type={!this.state.previewMode ? 'default' : 'primary'}
                style={{width: 80}}
                onClick={() => this.setPreviewMode(true)}>
                <Icon type="picture" />Preview
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
          >
            {getFieldDecorator('notification.subject', {
              initialValue: rule.notification
                ? rule.notification.subject
                : undefined,
              rules: [{
                required: !notificationsDisabled,
                message: ' '
              }]
            })(
              <Input
                disabled={notificationsDisabled || pending}
              />
            )}
          </Form.Item>
          <Row
            style={{
              display: previewMode ? 'flex' : 'none',
              marginBottom: 5
            }}
            type="flex"
            align="middle"
          >
            <Col offset={3}>
              Subject:
            </Col>
            <Col>
              <EmailPreview
                style={{marginLeft: 5}}
                iFrameStyle={{
                  height: 34,
                  width: '100%',
                  overflow: 'hidden',
                  border: 'transparent'
                }}
                value={form.getFieldValue('notification.subject')}
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
          >
            {getFieldDecorator('notification.body', {
              valuePropName: 'code',
              initialValue: rule.notification
                ? rule.notification.body
                : undefined
            })(
              <CodeEditor
                className={classNames(styles.codeEditor, 'cp-code-editor')}
                language="application/x-jsp"
                lineWrapping
                readOnly={notificationsDisabled || pending}
              />
            )}
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
                value={form.getFieldValue('notification.body')}
              />
            </Col>
            <Col offset={2} />
          </div>
        </Row>
      </div>
    );
  }
}

NotificationForm.propTypes = {
  form: PropTypes.object,
  rule: PropTypes.object,
  notificationsDisabled: PropTypes.bool
};

export default NotificationForm;
