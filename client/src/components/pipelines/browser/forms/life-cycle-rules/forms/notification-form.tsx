/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {useCallback, useEffect, useRef, useState} from 'react';
import classNames from 'classnames';
import {Button, Checkbox, Col, Form, Input, Row, Space} from 'antd';
import type {FormInstance} from 'antd';
import {EditOutlined, PictureOutlined} from '@ant-design/icons';
import UsersRolesSelect from '../../../../../special/users-roles-select';
import CodeEditor from '../../../../../special/CodeEditor';
import EmailPreview from '../../../../../../components/settings/forms/EmailPreview';
import {useNotificationTemplate} from '../hooks';
import type {Rule} from '../types';
import styles from './life-cycle-forms.module.css';

const columnLayout = {
  labelCol: {xs: {span: 24}, sm: {span: 10}},
  wrapperCol: {xs: {span: 24}, sm: {span: 10}},
};

const fullWidthLayout = {
  labelCol: {xs: {span: 24}, sm: {span: 5}},
  wrapperCol: {xs: {span: 24}, sm: {span: 17}},
};

interface NotificationFormProps {
  form: FormInstance;
  rule: Rule;
  notificationsDisabled?: boolean;
  useDefaultNotify?: boolean;
  onChangeUseDefaultNotify?: (checked: boolean) => void;
}

export default function NotificationForm({
  form,
  rule,
  notificationsDisabled,
  useDefaultNotify,
  onChangeUseDefaultNotify,
}: NotificationFormProps) {
  const [previewMode, setPreviewMode] = useState(false);
  const {pending} = useNotificationTemplate(rule?.id);
  const containerRef = useRef<HTMLDivElement>(null);

  const notifyUsers = Form.useWatch(['notification', 'notifyUsers'], form);

  const checkRequiredFields = useCallback(() => {
    setTimeout(() => {
      form
        .validateFields([
          ['notification', 'recipients'],
          ['notification', 'body'],
          ['notification', 'subject'],
        ])
        .catch(() => {});
    });
  }, [form]);

  useEffect(() => {
    checkRequiredFields();
  }, [notificationsDisabled, checkRequiredFields]);

  const handleChangeUseDefaultNotify = useCallback(
    (e: {target: {checked: boolean}}) => {
      onChangeUseDefaultNotify?.(e.target.checked);
    },
    [onChangeUseDefaultNotify],
  );

  const renderNotificationTemplate = () => {
    if (useDefaultNotify) return null;
    return (
      <div>
        <Row justify="start" style={{marginBottom: 5}}>
          <Col offset={3}>
            <Space.Compact size="small">
              <Button
                type={previewMode ? 'default' : 'primary'}
                style={{width: 80}}
                onClick={() => setPreviewMode(false)}
              >
                <EditOutlined />
                Edit
              </Button>
              <Button
                type={!previewMode ? 'default' : 'primary'}
                style={{width: 80}}
                onClick={() => setPreviewMode(true)}
              >
                <PictureOutlined />
                Preview
              </Button>
            </Space.Compact>
          </Col>
        </Row>
        <Row>
          <Form.Item
            {...fullWidthLayout}
            className={styles.formItem}
            label="Subject"
            hidden={previewMode}
            name={['notification', 'subject']}
            rules={[{required: !notificationsDisabled, message: ' '}]}
          >
            <Input disabled={notificationsDisabled || pending} />
          </Form.Item>
          <Row
            style={{display: previewMode ? 'flex' : 'none', marginBottom: 5}}
            align="middle"
          >
            <Col
              {...fullWidthLayout.labelCol}
              style={{padding: '0px 10px 3px 0px', textAlign: 'right'}}
            >
              Subject:
            </Col>
            <Col {...fullWidthLayout.wrapperCol}>
              <EmailPreview
                iFrameStyle={{
                  height: 34,
                  width: '100%',
                  overflow: 'hidden',
                  border: 'transparent',
                }}
                value={form.getFieldValue(['notification', 'subject'])}
              />
            </Col>
          </Row>
        </Row>
        <Row>
          <Form.Item
            {...fullWidthLayout}
            className={styles.formItem}
            label="Notification"
            hidden={previewMode}
            name={['notification', 'body']}
            valuePropName="code"
            rules={[{required: !notificationsDisabled, message: ' '}]}
          >
            <CodeEditor
              className={classNames(styles.codeEditor, 'cp-code-editor')}
              language="application/x-jsp"
              lineWrapping
              readOnly={notificationsDisabled || pending}
            />
          </Form.Item>
          <div style={{display: previewMode ? 'flex' : 'none'}}>
            <Col offset={3} style={{width: '100%'}}>
              <EmailPreview
                className={classNames(styles.codeEditor, 'cp-code-editor', 'cp-bordered')}
                style={{
                  lineHeight: 'inherit',
                  backgroundColor: 'transparent',
                  overflow: 'hidden',
                  width: '100%',
                  borderRadius: 4,
                }}
                value={form.getFieldValue(['notification', 'body'])}
              />
            </Col>
            <Col offset={2} />
          </div>
        </Row>
      </div>
    );
  };

  return (
    <div
      style={{width: '100%'}}
      ref={containerRef}
      className={styles.notificationsForm}
    >
      <Row>
        <Form.Item
          className={styles.formItem}
          style={{marginLeft: 10}}
          name={['notification', 'disabled']}
          valuePropName="checked"
        >
          <Checkbox disabled={pending}>Disable all notifications for the current rule</Checkbox>
        </Form.Item>
      </Row>
      <Row>
        <Form.Item
          {...fullWidthLayout}
          className={styles.formItem}
          label="Recipients"
          name={['notification', 'recipients']}
          rules={[{required: !notificationsDisabled && !notifyUsers, message: ' '}]}
        >
          <UsersRolesSelect
            disabled={notificationsDisabled || pending}
            style={{flex: 1}}
            styles={{popup: {root: {maxHeight: '80%'}}}}
            popupContainerFn={() => containerRef.current}
            onChange={checkRequiredFields}
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
          <Checkbox disabled={notificationsDisabled || pending} onChange={checkRequiredFields}>
            Storage users
          </Checkbox>
        </Form.Item>
      </Row>
      <Row justify="space-between">
        <Col style={{width: '50%'}}>
          <Form.Item
            {...columnLayout}
            className={styles.formItem}
            label="Notice period (days)"
            name={['notification', 'notifyBeforeDays']}
          >
            <Input disabled={notificationsDisabled || pending} />
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
            <Input disabled={notificationsDisabled || pending} />
          </Form.Item>
        </Col>
      </Row>
      <Row>
        <Checkbox
          checked={useDefaultNotify}
          onChange={handleChangeUseDefaultNotify}
          className={styles.formItem}
          style={{marginLeft: 10}}
          disabled={notificationsDisabled || pending}
        >
          Use default template
        </Checkbox>
      </Row>
      {renderNotificationTemplate()}
    </div>
  );
}
