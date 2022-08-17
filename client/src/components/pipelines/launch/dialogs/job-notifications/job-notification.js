/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Button, Icon, Input, Select} from 'antd';
import classNames from 'classnames';
import notificationValidationError from './notification-validation-error';
import compareArrays from '../../../../../utils/compareArrays';
import NotificationSettings from '../../../../../models/settings/NotificationSettings';
import StatusIcon from '../../../../special/run-status-icon';
import UserName from '../../../../special/UserName';
import CodeEditor from '../../../../special/CodeEditor';
import styles from './job-notifications.css';

const notificationSettings = new NotificationSettings();

export function mapObservableNotification (notification) {
  if (!notification) {
    return undefined;
  }
  const {
    type,
    recipients = [],
    triggerStatuses = [],
    subject,
    body
  } = notification;
  return {
    type,
    recipients: recipients.slice(),
    triggerStatuses: triggerStatuses.slice(),
    subject,
    body
  };
}

@inject('usersInfo')
@inject((stores) => ({
  ...stores,
  templates: notificationSettings
}))
@observer
class JobNotification extends React.Component {
  state = {
    type: undefined,
    recipients: [],
    triggerStatuses: [],
    subject: undefined,
    body: undefined,
    validationErrors: {}
  }

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.type !== this.props.type ||
      !compareArrays(prevProps.recipients, this.props.recipients) ||
      !compareArrays(prevProps.triggerStatuses, this.props.triggerStatuses) ||
      prevProps.body !== this.props.body ||
      prevProps.subject !== this.props.subject ||
      prevProps.reload !== this.props.reload
    ) {
      this.updateState();
    }
  }

  get statuses () {
    return [
      'FAILURE',
      'PAUSED',
      'PAUSING',
      'RESUMING',
      'RUNNING',
      'STOPPED',
      'SUCCESS'
    ];
  }

  updateState = () => {
    const {
      type,
      recipients = [],
      triggerStatuses = [],
      subject,
      body,
      defaultNotificationType
    } = this.props;
    this.setState({
      type: type || defaultNotificationType,
      recipients,
      triggerStatuses,
      subject,
      body,
      validationErrors: {}
    }, () => {
      if (this.editor) {
        this.editor.setValue(body);
      }
      return this.validate();
    });
  };

  validate = () => new Promise((resolve) => {
    const {
      type,
      recipients,
      triggerStatuses,
      body,
      subject
    } = this.state;
    const errors = notificationValidationError({
      type,
      recipients,
      triggerStatuses,
      body,
      subject
    });
    this.setState({
      validationErrors: errors
    }, () => {
      resolve(!(Object.values(errors).some(Boolean)));
    });
  });

  handleChange = () => {
    const {onChange} = this.props;
    const {
      type,
      recipients,
      triggerStatuses,
      subject,
      body
    } = this.state;
    onChange && onChange({
      type,
      recipients,
      triggerStatuses,
      subject,
      body
    });
    return this.validate();
  };

  onChangeType = (newType) => {
    this.setState({
      type: newType
    }, this.handleChange);
  };

  onChangeTriggerStatuses = statuses => {
    this.setState({
      triggerStatuses: statuses
    }, this.handleChange);
  }

  onChangeRecipients = recipients => {
    this.setState({
      recipients
    }, this.handleChange);
  };

  onChangeSubject = e => {
    this.setState({
      subject: e.target.value
    }, this.handleChange);
  };

  onChangeBody = body => {
    this.setState({
      body
    }, this.handleChange);
  };

  handleRemove = () => {
    const {onRemove, removable} = this.props;
    removable && onRemove && onRemove();
  };

  initializeEditor = editor => {
    this.editor = editor;
  };

  render () {
    const {
      availableNotificationTypes,
      templates: templatesRequest,
      usersInfo,
      removable
    } = this.props;
    const {
      type,
      triggerStatuses,
      recipients,
      body,
      subject,
      validationErrors
    } = this.state;
    const templates = templatesRequest.loaded
      ? (templatesRequest.value || []).map(template => template.type)
      : [];
    let filteredTemplates = [...templates];
    const availableNotificationTypesSet = new Set(availableNotificationTypes || []);
    if (availableNotificationTypesSet.size > 0) {
      filteredTemplates = [...templates]
        .filter(template => availableNotificationTypesSet.has(template));
    }
    return (
      <div
        className={
          classNames(
            styles.notification,
            'cp-even-odd-element'
          )
        }
      >
        <div className={styles.row}>
          {
            (
              filteredTemplates.length > 1 ||
              !!validationErrors.type
            ) && (
              <div className={styles.labeledGroup}>
                <span
                  className={
                    classNames(
                      styles.label,
                      {
                        'cp-error': validationErrors.type
                      }
                    )
                  }
                >
                  Type:
                </span>
                <Select
                  disabled={!templatesRequest.loaded}
                  value={type}
                  onChange={this.onChangeType}
                  className={
                    classNames(
                      styles.value,
                      {
                        'cp-error': validationErrors.type
                      }
                    )
                  }
                >
                  {
                    filteredTemplates
                      .map(template => (
                        <Select.Option key={template} value={template}>
                          {template}
                        </Select.Option>
                      ))
                  }
                </Select>
              </div>
            )
          }
          <div
            className={styles.labeledGroup}
            style={{flex: 1}}
          >
            <span
              className={
                classNames(
                  styles.label,
                  {
                    'cp-error': validationErrors.triggerStatuses
                  }
                )
              }
            >
              Statuses:
            </span>
            <Select
              mode="multiple"
              value={triggerStatuses}
              onChange={this.onChangeTriggerStatuses}
              className={
                classNames(
                  styles.value,
                  {
                    'cp-error': validationErrors.triggerStatuses
                  }
                )
              }
              filterOption={
                (input, option) =>
                  option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
              }
            >
              {
                (this.statuses)
                  .map(status => (
                    <Select.Option key={status} value={status}>
                      <StatusIcon
                        status={status}
                        className={styles.statusIcon}
                      /> {status}
                    </Select.Option>
                  ))
              }
            </Select>
          </div>
          <div
            className={styles.labeledGroup}
            style={{flex: 1}}
          >
            <span
              className={
                classNames(
                  styles.label,
                  {
                    'cp-error': validationErrors.recipients
                  }
                )
              }
            >
              Recipients:
            </span>
            <Select
              disabled={!usersInfo.loaded}
              mode="multiple"
              value={recipients}
              onChange={this.onChangeRecipients}
              className={
                classNames(
                  styles.value,
                  {
                    'cp-error': validationErrors.recipients
                  }
                )
              }
              filterOption={
                (input, option) => (option.props.attributes.split('|').map(o => o.toLowerCase()))
                  .find(o => o.includes((input || '').toLowerCase()))
              }
            >
              {
                (usersInfo.loaded ? (usersInfo.value || []) : [])
                  .map(user => (
                    <Select.Option
                      key={user.name}
                      value={user.name}
                      attributes={
                        [
                          user.name,
                          ...Object.values(user.attributes || {})
                        ]
                          .join('|')
                      }
                    >
                      <UserName userName={user.name} />
                    </Select.Option>
                  ))
              }
            </Select>
          </div>
        </div>
        <div className={styles.row}>
          <div
            className={styles.labeledGroup}
            style={{flex: 1}}
          >
            <span
              className={
                classNames(
                  styles.label,
                  {
                    'cp-error': validationErrors.subject
                  }
                )
              }
            >
              Subject:
            </span>
            <Input
              className={
                classNames(
                  styles.value,
                  {
                    'cp-error': validationErrors.subject
                  }
                )
              }
              value={subject}
              onChange={this.onChangeSubject}
            />
          </div>
        </div>
        <div className={styles.row}>
          <div
            className={styles.labeledGroup}
            style={{alignItems: 'flex-start', flex: 1}}
          >
            <span
              className={
                classNames(
                  styles.label,
                  {
                    'cp-error': validationErrors.body
                  }
                )
              }
            >
              Body:
            </span>
            <CodeEditor
              ref={this.initializeEditor}
              className={
                classNames(
                  styles.value,
                  {
                    'cp-error': validationErrors.body
                  }
                )
              }
              language="application/x-jsp"
              onChange={this.onChangeBody}
              lineWrapping
              defaultCode={body}
            />
          </div>
        </div>
        {
          removable && (
            <div
              className={styles.row}
              style={{justifyContent: 'flex-end'}}
            >
              <Button
                size="small"
                type="danger"
                onClick={this.handleRemove}
              >
                <Icon type="delete" /> Remove
              </Button>
            </div>
          )
        }
      </div>
    );
  }
}

JobNotification.propTypes = {
  type: PropTypes.string,
  recipients: PropTypes.array,
  subject: PropTypes.string,
  body: PropTypes.string,
  triggerStatuses: PropTypes.array,
  availableNotificationTypes: PropTypes.array,
  defaultNotificationType: PropTypes.string,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  removable: PropTypes.bool,
  error: PropTypes.string,
  reload: PropTypes.any
};

export default JobNotification;
