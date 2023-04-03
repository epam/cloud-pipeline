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
import {Button, Checkbox, Icon, Modal} from 'antd';
import classNames from 'classnames';
import {notificationArraysAreEqual} from './notifications-equal';
import JobNotification from './job-notification';
import notificationValidationError from './notification-validation-error';
import UserName from '../../../../special/UserName';
import styles from './job-notifications.css';

class JobNotifications extends React.Component {
  state = {
    notifications: [],
    visible: false
  };

  componentDidMount () {
    this.updateState();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (!notificationArraysAreEqual(prevProps.value, this.props.value)) {
      this.updateState();
    }
  }

  get valid () {
    const {multiple} = this.props;
    const {notifications = []} = this.state;
    if (!multiple && notifications.length !== 1) {
      return false;
    }
    return !(
      notifications
        .map(notificationValidationError)
        .map(error => !(Object.values(error || {}).some(Boolean)))
        .some(valid => !valid)
    );
  }

  get modified () {
    const {value: initial = []} = this.props;
    const {notifications = []} = this.state;
    return !notificationArraysAreEqual(notifications, initial);
  }

  updateState = () => new Promise(resolve => {
    const {
      value: notifications = []
    } = this.props;
    this.setState({
      notifications: notifications.map(o => ({...o}))
    }, () => resolve());
  });

  handleChange = () => {
    const {onChange} = this.props;
    const {notifications = []} = this.state;
    onChange && onChange([...notifications]);
    return this.closeConfigurationDialog();
  };

  handleVisibility = (visible) => new Promise((resolve) => {
    this.setState({
      visible
    }, () => resolve());
  });

  openConfigurationDialog = () => {
    return this.handleVisibility(true);
  }

  closeConfigurationDialog = () => {
    return this.handleVisibility(false);
  }

  renderTitle = () => {
    const {
      value: notifications = [],
      multiple,
      linkClassName,
      linkStyle
    } = this.props;
    if (notifications.length === 0) {
      return null;
    }
    let title = (
      <span>
        Configure
      </span>
    );
    if (notifications.length > 0) {
      const [first] = notifications;
      const {
        triggerStatuses = [],
        recipients = []
      } = first;
      if (multiple && notifications.length > 1) {
        title = (
          <span>
            {`${notifications.length} notification${notifications.length > 1 ? 's' : ''}`}
          </span>
        );
      } else if (triggerStatuses.length > 0) {
        title = [
          <span key="title">
            Notification will be sent on
          </span>,
          triggerStatuses.map(status => (
            <span
              key={status}
              style={{marginLeft: 5}}
            >
              {status}
            </span>
          )),
          <span key="footer" style={{marginLeft: 5}}>
            {`status${triggerStatuses.length > 1 ? 'es' : ''}`}
          </span>
        ];
      } else if (recipients.length > 0) {
        title = [
          <span key="title">
            Notification will be sent to
          </span>,
          recipients.map(recipient => (
            <UserName
              key={recipient}
              userName={recipient}
              style={{marginLeft: 5}}
            />
          ))
        ];
      }
    }
    return (
      <div
        className={
          classNames(
            'cp-text',
            'underline',
            styles.link,
            linkClassName
          )
        }
        style={linkStyle}
        onClick={this.openConfigurationDialog}
      >
        <Icon type="setting" />
        {title}
      </div>
    );
  };

  cancelClicked = () => {
    this.closeConfigurationDialog()
      .then(this.updateState);
  };

  clearNotifications = () => new Promise((resolve) => {
    this.setState({
      notifications: []
    }, () => resolve());
  });

  addNotification = () => new Promise((resolve) => {
    const {
      defaultNotificationType
    } = this.props;
    const {notifications = []} = this.state;
    this.setState({
      notifications: [
        ...notifications,
        {
          type: defaultNotificationType,
          recipients: [],
          triggerStatuses: []
        }
      ]
    }, () => resolve());
  });

  removeNotification = (index) => () => {
    const {multiple} = this.props;
    const {notifications = []} = this.state;
    if (
      (!multiple && notifications.length < 2) ||
      index < 0 ||
      notifications.length <= index
    ) {
      return;
    }
    notifications.splice(index, 1);
    this.setState({
      notifications: [...notifications]
    });
  };

  changeNotification = (index) => (notification) => {
    const {notifications = []} = this.state;
    if (index < 0 || notifications.length <= index) {
      return;
    }
    notifications.splice(index, 1, {...notification});
    this.setState({
      notifications: [...notifications]
    });
  };

  enableNotifications = (e) => {
    const {notifications} = this.state;
    const enable = e.target.checked;
    if (enable && notifications.length === 0) {
      this.addNotification()
        .then(this.handleChange);
    } else if (!enable && notifications.length > 0) {
      this.clearNotifications()
        .then(this.handleChange);
    }
  };

  render () {
    const {
      availableNotificationTypes,
      defaultNotificationType,
      multiple
    } = this.props;
    const {
      visible,
      notifications
    } = this.state;
    return (
      <div className={styles.container}>
        <Checkbox
          checked={notifications.length > 0}
          onChange={this.enableNotifications}
        >
          Enabled
        </Checkbox>
        {this.renderTitle()}
        <Modal
          visible={visible}
          title={`Configure job notification${multiple ? 's' : ''}`}
          onCancel={this.cancelClicked}
          width="75%"
          footer={(
            <div
              className={styles.footer}
            >
              <div className={styles.buttonsGroup}>
                <Button
                  onClick={this.cancelClicked}
                >
                  CANCEL
                </Button>
                {
                  multiple && (
                    <Button
                      disabled={notifications.length === 0}
                      className={styles.button}
                      onClick={this.clearNotifications}
                      type="DANGER"
                    >
                      CLEAR
                    </Button>
                  )
                }
              </div>
              <Button
                className={styles.button}
                disabled={!this.modified || !this.valid}
                type="primary"
                onClick={this.handleChange}
              >
                SAVE
              </Button>
            </div>
          )}
        >
          {
            notifications.map((notification, index) => (
              <JobNotification
                key={`${notification.type}-${index}`}
                type={notification.type}
                subject={notification.subject}
                body={notification.body}
                recipients={notification.recipients}
                triggerStatuses={notification.triggerStatuses}
                availableNotificationTypes={availableNotificationTypes}
                defaultNotificationType={defaultNotificationType}
                removable={multiple}
                onRemove={this.removeNotification(index)}
                onChange={this.changeNotification(index)}
                reload={visible}
              />
            ))
          }
          {
            notifications.length === 0 && (
              <div className={styles.noNotificationsWarning}>
                {/* eslint-disable-next-line */}
                <span>Notifications are not configured.</span>
                <a
                  onClick={this.addNotification}
                  style={{marginLeft: 5}}
                >
                  Create notification
                </a>
              </div>
            )
          }
          {
            multiple && notifications.length > 0 && (
              <div>
                <Button
                  size="small"
                  onClick={this.addNotification}
                >
                  <Icon type="plus" /> Add notification
                </Button>
              </div>
            )
          }
        </Modal>
      </div>
    );
  }
}

JobNotifications.propTypes = {
  value: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  onChange: PropTypes.func,
  availableNotificationTypes: PropTypes.array,
  defaultNotificationType: PropTypes.string,
  multiple: PropTypes.bool,
  linkClassName: PropTypes.string,
  linkStyle: PropTypes.object
};

JobNotifications.defaultProps = {
  availableNotificationTypes: ['PIPELINE_RUN_STATUS'],
  defaultNotificationType: 'PIPELINE_RUN_STATUS',
  multiple: false
};

export default JobNotifications;
