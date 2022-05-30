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
import classNames from 'classnames';
import {
  Button,
  Modal,
  Icon,
  Input,
  Select,
  Tooltip
} from 'antd';
import {observer} from 'mobx-react';
import UsersRolesSelect from '../../../users-roles-select';
import roleModel from '../../../../../utils/roleModel';
import styles from './fs-notifications.css';

const VALUE_TITLE = 'Volume threshold';

const NotificationTypes = {
  gb: 'GB',
  percent: 'PERCENT'
};

const NotificationTypeNames = {
  [NotificationTypes.gb]: 'Gb',
  [NotificationTypes.percent]: '%'
};

const NotificationActions = {
  email: 'EMAIL',
  disableMount: 'DISABLE_MOUNT',
  readOnly: 'READONLY'
};

const NotificationActionNames = {
  [NotificationActions.email]: 'Send email',
  [NotificationActions.disableMount]: 'Disable mount',
  [NotificationActions.readOnly]: 'Make read-only'
};

function plural (count, itemName) {
  return `${count} ${itemName}${count !== 1 ? 's' : ''}`;
}

function getNotificationPresentation (notification) {
  const {
    type = 'GB',
    value = 0,
    actions = []
  } = notification;
  return `${value}${type}:${actions.sort().join(',')}`;
}

function notificationsEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  const aPresentations = (a || []).map(getNotificationPresentation).sort().join('|');
  const bPresentations = (b || []).map(getNotificationPresentation).sort().join('|');
  return aPresentations === bPresentations;
}

function recipientsEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  const aUsers = [...(new Set((a || []).filter(o => o.principal).map(o => o.name)))].sort();
  const bUsers = [...(new Set((b || []).filter(o => o.principal).map(o => o.name)))].sort();
  const aRoles = [...(new Set((a || []).filter(o => !o.principal).map(o => o.name)))].sort();
  const bRoles = [...(new Set((b || []).filter(o => !o.principal).map(o => o.name)))].sort();
  if (aUsers.length !== bUsers.length || aRoles.length !== bRoles.length) {
    return false;
  }
  for (let i = 0; i < aUsers.length; i++) {
    if (aUsers[i] !== bUsers[i]) {
      return false;
    }
  }
  for (let i = 0; i < aRoles.length; i++) {
    if (aRoles[i] !== bRoles[i]) {
      return false;
    }
  }
  return true;
}

function getNotificationValidationError (notification) {
  const {actions, type, value} = notification;
  const error = {};
  if (value === undefined) {
    error.value = 'Threshold is required';
  }
  if (!type) {
    error.type = 'Threshold type is required';
  }
  if (type === NotificationTypes.gb) {
    if (Number.isNaN(Number(value)) || Number(value) <= 0) {
      error.value = 'Threshold must be a positive number';
    }
  } else if (Number.isNaN(Number(value)) || Number(value) <= 0 || Number(value) > 100) {
    error.value = 'Threshold must be a positive number (0..100)';
  }
  if (!actions || actions.length === 0) {
    error.actions = 'You must specify an action';
  } else if (
    actions.includes(NotificationActions.disableMount) &&
    actions.includes(NotificationActions.readOnly)
  ) {
    const readOnlyAction = NotificationActionNames[NotificationActions.readOnly];
    const disableMountAction = NotificationActionNames[NotificationActions.disableMount];
    error.actions = `You must specify either "${readOnlyAction}" or "${disableMountAction}" action`;
  }
  if (Object.values(error).length === 0) {
    return undefined;
  }
  return error;
}

let fakeIdentifier = 0;

function uid () {
  fakeIdentifier += 1;
  return fakeIdentifier;
}

class FSNotificationsDialog extends React.Component {
  state = {
    notifications: [],
    recipients: []
  };

  get valid () {
    const {
      notifications,
      recipients = []
    } = this.state;
    return (notifications.length === 0 || recipients.length > 0) &&
      !(notifications.map(u => u.error).find(o => !!o));
  }

  get availableNotificationTypes () {
    const {
      info = {}
    } = this.props;
    const {storageType} = info;
    return Object
      .values(NotificationTypes)
      .filter(o => /^lustre$/i.test(storageType) || o === NotificationTypes.gb);
  }

  get notificationTypesDisabledWarning () {
    const {
      info = {}
    } = this.props;
    const {storageType} = info;
    if (!/^lustre$/i.test(storageType)) {
      const message = 'Only GB quota type is available';
      if (storageType) {
        return `${message} for ${storageType} mounts `;
      }
      return message;
    }
    return undefined;
  }

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      (prevProps.visible !== this.props.visible && this.props.visible) ||
      (
        !recipientsEqual(prevProps.notifications, this.props.notifications) ||
        !notificationsEqual(prevProps.notifications, this.props.notifications)
      )
    ) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {notifications = [], recipients = []} = this.props;
    this.setState({
      notifications: notifications.map(n => ({...n, id: uid(), error: undefined})),
      recipients
    });
  };

  validate = () => {
    return new Promise((resolve) => {
      const {notifications = []} = this.state;
      this.setState({
        // eslint-disable-next-line handle-callback-err
        notifications: notifications.map(({error, ...n}) => ({
          ...n,
          error: getNotificationValidationError(n)
        }))
      }, () => {
        resolve(this.valid);
      });
    });
  };

  onChangeNotificationValue = (id) => (e) => {
    const {notifications = []} = this.state;
    const notification = notifications.find(n => n.id === id);
    if (notification) {
      notification.value = e.target.value;
      this.setState({notifications: notifications.slice()}, this.validate);
    }
  };

  onChangeNotificationType = (id) => (type) => {
    const {notifications = []} = this.state;
    const notification = notifications.find(n => n.id === id);
    if (notification) {
      notification.type = type;
      this.setState({notifications: notifications.slice()}, this.validate);
    }
  };

  onChangeNotificationActions = (id) => (actions) => {
    const {notifications = []} = this.state;
    const notification = notifications.find(n => n.id === id);
    if (notification) {
      notification.actions = (actions || []).slice();
      this.setState({notifications: notifications.slice()}, this.validate);
    }
  };

  onRemoveNotification = (id) => () => {
    const {notifications = []} = this.state;
    const notificationIndex = notifications.findIndex(n => n.id === id);
    if (notificationIndex >= 0) {
      notifications.splice(notificationIndex, 1);
      this.setState({notifications: notifications.slice()}, this.validate);
    }
  };

  onAddNotification = () => {
    const {notifications = []} = this.state;
    this.setState({
      notifications: [
        ...notifications,
        {
          id: uid(),
          type: this.availableNotificationTypes[0],
          actions: [NotificationActions.email]
        }
      ]
    }, this.validate);
  };

  clearNotification = () => {
    this.setState({
      notifications: []
    }, this.validate);
  };

  clearRecipients = () => {
    this.setState({
      recipients: []
    }, this.validate);
  };

  onChangeRecipients = (recipients) => {
    this.setState({
      recipients: (recipients || []).slice()
    }, this.validate);
  };

  onOk = () => {
    const {onChange} = this.props;
    const {
      notifications = [],
      recipients = []
    } = this.state;
    onChange && onChange(
      // eslint-disable-next-line handle-callback-err
      notifications.map(({error, id, ...notification}) => notification),
      recipients
    );
  };

  render () {
    const {
      visible,
      onClose,
      readOnly
    } = this.props;
    const {
      notifications = [],
      recipients = []
    } = this.state;
    const emptyRecipients = recipients.length === 0 && notifications.length > 0;
    let NotificationTypesWrapper = ({children}) => children;
    if (this.notificationTypesDisabledWarning) {
      NotificationTypesWrapper = ({children}) => (
        <Tooltip
          title={this.notificationTypesDisabledWarning}
          trigger="hover"
        >
          {children}
          <Icon
            type="exclamation-circle-o"
            style={{
              marginRight: 5
            }}
          />
        </Tooltip>
      );
    }
    return (
      <Modal
        title="Configure FS mount notifications"
        visible={visible}
        onCancel={onClose}
        width="800px"
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              onClick={onClose}
            >
              CANCEL
            </Button>
            <Button
              disabled={readOnly || !this.valid}
              type="primary"
              onClick={this.onOk}
            >
              OK
            </Button>
          </div>
        )}
      >
        <div
          className={
            classNames(
              styles.usersRolesSelect,
              'cp-divider',
              'bottom',
              {
                [styles.error]: emptyRecipients,
                'cp-error': emptyRecipients
              }
            )
          }
        >
          <span className={styles.label}>
            Recipients:
          </span>
          <UsersRolesSelect
            className={
              classNames(
                {
                  'cp-error': emptyRecipients
                }
              )
            }
            disabled={readOnly}
            value={recipients}
            onChange={this.onChangeRecipients}
            style={{flex: 1, marginRight: 5}}
          />
          <Button
            disabled={readOnly || recipients.length === 0}
            size="small"
            type="danger"
            onClick={this.clearRecipients}
          >
            <Icon type="delete" /> Clear all recipients
          </Button>
        </div>
        <div
          className={
            classNames(
              styles.usersRolesSelectError,
              'cp-error',
              {
                [styles.visible]: emptyRecipients
              }
            )
          }
        >
          You must specify recipients
        </div>
        <div>
          {
            notifications.map((notification) => [
              (
                <div
                  key={notification.id}
                  className={
                    classNames(
                      styles.notification,
                      'cp-metadata-fs-notification',
                      {
                        'cp-error': !!notification.error
                      }
                    )
                  }
                >
                  <span
                    className={
                      classNames(
                        styles.label,
                        {
                          'cp-error': !!notification.error && !!notification.error.value
                        }
                      )
                    }
                  >
                    {VALUE_TITLE}:
                  </span>
                  <Input
                    disabled={readOnly}
                    className={
                      classNames(
                        styles.input,
                        {
                          'cp-error': !!notification.error && !!notification.error.value
                        }
                      )
                    }
                    value={notification.value || ''}
                    onChange={this.onChangeNotificationValue(notification.id)}
                  />
                  <NotificationTypesWrapper>
                    <Select
                      disabled={readOnly || this.availableNotificationTypes.length === 1}
                      className={
                        classNames(
                          styles.select,
                          {
                            'cp-error': !!notification.error && !!notification.error.type
                          }
                        )
                      }
                      value={notification.type}
                      onChange={this.onChangeNotificationType(notification.id)}
                    >
                      {
                        this.availableNotificationTypes
                          .map((notificationType) => (
                            <Select.Option
                              key={notificationType}
                              value={notificationType}
                            >
                              {NotificationTypeNames[notificationType]}
                            </Select.Option>
                          ))
                      }
                    </Select>
                  </NotificationTypesWrapper>
                  <span
                    className={
                      classNames(
                        styles.label,
                        {
                          'cp-error': !!notification.error && !!notification.error.actions
                        }
                      )
                    }
                  >
                    Actions:
                  </span>
                  <Select
                    disabled={readOnly}
                    className={
                      classNames(
                        styles.select,
                        {
                          'cp-error': !!notification.error && !!notification.error.actions
                        }
                      )
                    }
                    mode="multiple"
                    value={notification.actions || []}
                    onChange={this.onChangeNotificationActions(notification.id)}
                    style={{flex: 1}}
                    placeholder="Do nothing"
                  >
                    {
                      Object
                        .values(NotificationActions || {})
                        .map((notificationActionType) => (
                          <Select.Option
                            key={notificationActionType}
                            value={notificationActionType}
                          >
                            {NotificationActionNames[notificationActionType]}
                          </Select.Option>
                        ))
                    }
                  </Select>
                  <Button
                    size="small"
                    type="danger"
                    disabled={readOnly}
                    onClick={this.onRemoveNotification(notification.id)}
                  >
                    <Icon type="delete" />
                  </Button>
                </div>
              ),
              (
                <div
                  className={
                    classNames(
                      styles.notification,
                      styles.notificationError,
                      'cp-error',
                      {
                        [styles.visible]: !!notification.error
                      }
                    )
                  }
                  key={`${notification.id}-error`}
                >
                  <ul>
                    {
                      Object.values(notification.error || {})
                        .map((error, index) => (
                          <li className={styles.errorDescription} key={index}>
                            {error}
                          </li>
                        ))
                    }
                  </ul>
                </div>
              )
            ])
              .reduce((r, c) => ([...r, ...c]), [])
          }
        </div>
        <div
          className={styles.actions}
        >
          <Button
            size="small"
            onClick={this.onAddNotification}
            disabled={readOnly}
          >
            <Icon type="plus" /> Add notification
          </Button>
          <Button
            size="small"
            type="danger"
            onClick={this.clearNotification}
            disabled={readOnly || notifications.length === 0}
          >
            <Icon type="delete" /> Clear all notifications
          </Button>
        </div>
      </Modal>
    );
  }
}

FSNotificationsDialog.propTypes = {
  notifications: PropTypes.array,
  recipients: PropTypes.array,
  visible: PropTypes.bool,
  onChange: PropTypes.func,
  onClose: PropTypes.func,
  readOnly: PropTypes.bool,
  info: PropTypes.object
};

@roleModel.authenticationInfo
@observer
class FSNotifications extends React.Component {
  state = {
    visible: false
  };

  get parsedMetadata () {
    const {metadata = {}} = this.props;
    const {value = '{}'} = metadata;
    try {
      return JSON.parse(value);
    } catch (_) {
      return {
        notifications: [],
        recipients: []
      };
    }
  }

  onOpenEditDialog = () => {
    this.setState({visible: true});
  };

  onCloseEditDialog = () => {
    this.setState({visible: false});
  };

  onChange = (notifications, recipients) => {
    const payload = notifications.length > 0 ? {notifications, recipients} : {};
    const value = JSON.stringify(payload);
    this.onCloseEditDialog();
    const {onChange} = this.props;
    onChange && onChange(value);
  };

  render () {
    const {
      authenticatedUserInfo,
      readOnly: readOnlyRaw,
      info = {},
      showOnlySummary
    } = this.props;
    const readOnly = showOnlySummary ||
      readOnlyRaw ||
      !authenticatedUserInfo ||
      !authenticatedUserInfo.loaded ||
      !authenticatedUserInfo.value ||
      !authenticatedUserInfo.value.admin;
    const {
      visible
    } = this.state;
    const {
      notifications = [],
      recipients = []
    } = this.parsedMetadata;
    let title = readOnly
      ? 'Notifications are not configured'
      : 'Configure notifications';
    let empty = notifications.length === 0;
    if (notifications.length > 0) {
      title = [
        plural(notifications.length, 'notification'),
        recipients.length > 0 ? plural(recipients.length, 'recipient') : false
      ].join(', ');
    }
    return (
      <div
        className={classNames(
          styles.container,
          {[styles.disabled]: showOnlySummary || (readOnly && notifications.length === 0)}
        )}
        onClick={showOnlySummary || (readOnly && notifications.length === 0)
          ? undefined
          : this.onOpenEditDialog
        }
      >
        {
          empty && !readOnly
            ? (<Icon type="setting" />)
            : (<Icon type="notification" style={{marginRight: 5}} />)
        }
        {title}
        <FSNotificationsDialog
          readOnly={readOnly}
          visible={visible}
          notifications={notifications}
          recipients={recipients}
          onClose={this.onCloseEditDialog}
          onChange={this.onChange}
          info={info}
        />
      </div>
    );
  }
}

FSNotifications.propTypes = {
  metadata: PropTypes.object,
  readOnly: PropTypes.bool,
  showOnlySummary: PropTypes.bool,
  onChange: PropTypes.func,
  info: PropTypes.object
};

const METADATA_KEY = 'fs_notifications';

FSNotifications.metatadaKey = METADATA_KEY;

export {METADATA_KEY};
export default FSNotifications;
