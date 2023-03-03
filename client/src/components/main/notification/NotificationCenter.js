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
import {inject, observer} from 'mobx-react';
import {computed, observable, action} from 'mobx';
import SystemNotification from './SystemNotification';
import {message, Modal, Button, Row, Icon} from 'antd';
import moment from 'moment-timezone';
import ConfirmNotification from '../../../models/notifications/ConfirmNotification';
import {DEFAULT_PAGE_SIZE} from '../../../models/notifications/CurrentUserNotifications';
import ReadAllUserNotifications from '../../../models/notifications/ReadAllUserNotifications';
import ReadMessage from '../../../models/notifications/ReadMessage';
import PreviewNotification from './PreviewNotification';
import Markdown from '../../special/markdown';
import styles from './SystemNotification.css';

const MAX_NOTIFICATIONS = 5;

const PredefinedNotifications = {
  MaintenanceMode: -1
};

const NOTIFICATION_TYPE = {
  message: 'message',
  notification: 'notification'
};

function dateSorter (notificationA, notificationB) {
  const dateA = moment.utc(notificationA.createdDate);
  const dateB = moment.utc(notificationB.createdDate);
  if (dateA > dateB) {
    return -1;
  } else if (dateA < dateB) {
    return 1;
  }
  return notificationA.notificationId
    .localeCompare(notificationB.notificationId);
}

function mapMessage (message) {
  return {
    title: message.subject,
    body: message.text,
    createdDate: message.createdDate,
    isRead: message.isRead,
    userId: message.userId,
    notificationId: `message_${message.id}`,
    type: NOTIFICATION_TYPE.message
  };
}

function unMapMessage (message) {
  return {
    subject: message.title,
    text: message.body,
    createdDate: message.createdDate,
    isRead: message.isRead,
    userId: message.userId,
    id: message.notificationId.split('message_').pop()
  };
}

@inject('notifications', 'userNotifications', 'preferences')
@observer
export default class NotificationCenter extends React.Component {
  static propTypes = {
    delaySeconds: PropTypes.number,
    disableEmailNotifications: PropTypes.bool
  };

  state = {
    notificationsState: [],
    hiddenNotifications: [],
    initialized: false,
    previewNotification: null
  };

  @observable _notificationsBottomBound = 0;
  @observable _notificationsOnScreen = 0;
  @observable readUserNotifications = 0;

  layoutInfoTimeout;
  resizeTimeout;
  fetchNotificationsTimeout;

  @computed
  get notificationsBottomBound () {
    return this._notificationsBottomBound;
  }

  @computed
  get notificationsOnScreen () {
    return this._notificationsOnScreen;
  }

  @computed
  get userNotifications () {
    const {userNotifications} = this.props;
    if (!userNotifications.loaded || !this.state.initialized || !this.userNotificationsEnabled) {
      return [];
    }
    const dateFrom = userNotifications.hideNotificationsTill;
    return [...(userNotifications.value.elements || [])]
      .filter(message => {
        return !message.isRead && (dateFrom
          ? moment.utc(message.createdDate) > dateFrom
          : true
        );
      })
      .map(mapMessage)
      .sort(dateSorter);
  }

  @computed
  get systemNotifications () {
    if (!this.props.notifications.loaded || !this.state.initialized) {
      return [];
    }
    const {systemMaintenanceMode, systemMaintenanceModeBanner} = this.props.preferences;
    const notifications = [...(this.props.notifications.value || [])];
    if (systemMaintenanceMode && systemMaintenanceModeBanner) {
      notifications.unshift({
        blocking: false,
        body: systemMaintenanceModeBanner,
        createdDate: '',
        notificationId: PredefinedNotifications.MaintenanceMode,
        severity: 'INFO',
        state: 'ACTIVE',
        title: 'Maintenance mode'
      });
    }
    return notifications.sort(dateSorter);
  }

  @computed
  get unreadCount () {
    const {userNotifications} = this.props;
    if (userNotifications.loaded) {
      return userNotifications.value.totalCount - this.readUserNotifications;
    }
    return 0;
  }

  @computed
  get allNotifications () {
    return [...this.systemNotifications, ...this.userNotifications];
  }

  @computed
  get nonBlockingNotifications () {
    return this.allNotifications
      .filter(n => !n.blocking);
  }

  get notificationsToShow () {
    const {hiddenNotifications} = this.state;
    return this.nonBlockingNotifications
      .filter(n => !hiddenNotifications.find(({id}) => id === n.notificationId))
      .slice(0, MAX_NOTIFICATIONS);
  }

  @computed
  get userNotificationsEnabled () {
    const {
      userNotifications,
      preferences,
      disableEmailNotifications
    } = this.props;
    if (userNotifications.loaded && preferences.loaded) {
      return !disableEmailNotifications &&
        preferences.userNotificationsEnabled &&
        !userNotifications.muted;
    }
    return false;
  }

  @action
  setVisibleNotificationsInfo = (notificationsBottomBound, notifications) => {
    if (this.layoutInfoTimeout) {
      clearTimeout(this.layoutInfoTimeout);
    }
    this.layoutInfoTimeout = setTimeout(() => {
      this._notificationsBottomBound = notificationsBottomBound;
      this._notificationsOnScreen = notifications;
    }, 100);
  };

  getHiddenNotifications = () => {
    const hiddenNotificationsInStorageStr = localStorage.getItem('hidden_notifications');
    if (hiddenNotificationsInStorageStr) {
      let hiddenNotificationsInStorage = [];
      try {
        hiddenNotificationsInStorage = JSON.parse(hiddenNotificationsInStorageStr);
        if (!Array.isArray(hiddenNotificationsInStorage)) {
          hiddenNotificationsInStorage = [];
        }
      } catch (___) {}
      return [...this.state.hiddenNotifications, ...hiddenNotificationsInStorage];
    }
    return this.state.hiddenNotifications;
  };

  notificationIsVisible = (notification) => {
    const isHidden = !!this.getHiddenNotifications()
      .find(n => n.id === notification.notificationId &&
      n.createdDate === notification.createdDate);
    const state = this.state.notificationsState
      .find(n => n.id === notification.notificationId && n.height !== undefined);
    return !isHidden && !!state;
  };

  getPositioningInfo = (notification, index) => {
    if (this.notificationIsVisible(notification)) {
      const notifications = this.notificationsToShow;
      const padding = 50;
      let top = SystemNotification.margin;
      let bottom = SystemNotification.margin;
      let remainingHeight = window.innerHeight - padding;
      let doesFit = true;
      let notificationsOnScreen = 0;
      for (let i = 0; i <= index; i++) {
        const previouslyRenderedItem = i !== index;
        const notificationItem = notifications[i];
        const [state] = this.state.notificationsState
          .filter(n => n.id === notificationItem.notificationId);
        if (
          this.notificationIsVisible(notificationItem) &&
          state &&
          state.height !== undefined
        ) {
          remainingHeight = window.innerHeight - top - state.height;
          doesFit = remainingHeight >= padding;
          if (doesFit) {
            top = previouslyRenderedItem
              ? top + state.height + SystemNotification.margin
              : top;
            bottom = previouslyRenderedItem
              ? top
              : top + state.height + SystemNotification.margin;
            notificationsOnScreen += 1;
          }
        }
      }
      this.setVisibleNotificationsInfo(bottom, notificationsOnScreen);
      return {
        top,
        visible: doesFit
      };
    } else {
      return {
        visible: false
      };
    }
  };

  onHeightInitialized = ({notificationId}, height) => {
    const notificationsState = this.state.notificationsState;
    let [state] = notificationsState.filter(s => s.id === notificationId);
    if (state) {
      state.height = height;
    } else {
      state = {
        id: notificationId,
        height
      };
      notificationsState.push(state);
    }
    this.setState({notificationsState});
  };

  readMessage = async (notification, forceRead = false) => {
    if (!notification) {
      return null;
    }
    const buffer = 5;
    const force = forceRead ||
      (DEFAULT_PAGE_SIZE - this.readUserNotifications <= MAX_NOTIFICATIONS + buffer);
    const request = new ReadMessage();
    const payload = unMapMessage(notification);
    payload.isRead = true;
    payload.readDate = moment.utc().format('YYYY-MM-DD HH:mm:ss.SSS');
    this.readUserNotifications += 1;
    await request.send(payload);
    if (request.error) {
      message.error(request.error, 5);
    }
    this.fetchUserNotifications(force);
  };

  onCloseNotification = async (notification) => {
    const hidden = this.state.hiddenNotifications;
    hidden.push({
      id: notification.notificationId,
      createdDate: notification.createdDate
    });
    if (notification.type === NOTIFICATION_TYPE.message) {
      this.readMessage(notification);
    }
    if (notification.blocking) {
      let hiddenNotificationsInStorage = [];
      const hiddenNotificationsInStorageStr = localStorage.getItem('hidden_notifications');
      if (hiddenNotificationsInStorageStr) {
        try {
          hiddenNotificationsInStorage = JSON.parse(hiddenNotificationsInStorageStr);
          if (!Array.isArray(hiddenNotificationsInStorage)) {
            hiddenNotificationsInStorage = [];
          }
        } catch (___) {}
      }
      hiddenNotificationsInStorage.push({
        id: notification.notificationId,
        createdDate: notification.createdDate
      });
      try {
        localStorage.setItem('hidden_notifications', JSON.stringify(hiddenNotificationsInStorage));
      } catch (___) {}
    }
    this.setState({hiddenNotifications: hidden});
  };

  onCloseBlockingNotification = async (notification) => {
    const hidden = this.state.hiddenNotifications;
    hidden.push({
      id: notification.notificationId,
      createdDate: notification.createdDate
    });
    const hide = message.loading('Confirming...', 0);
    const request = new ConfirmNotification();
    await request.send({
      notificationId: notification.notificationId,
      body: notification.body,
      title: notification.title
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      let hiddenNotificationsInStorage = [];
      const hiddenNotificationsInStorageStr = localStorage.getItem('hidden_notifications');
      if (hiddenNotificationsInStorageStr) {
        try {
          hiddenNotificationsInStorage = JSON.parse(hiddenNotificationsInStorageStr);
          if (!Array.isArray(hiddenNotificationsInStorage)) {
            hiddenNotificationsInStorage = [];
          }
        } catch (___) {}
      }
      hiddenNotificationsInStorage.push({
        id: notification.notificationId,
        createdDate: notification.createdDate
      });
      try {
        localStorage.setItem('hidden_notifications', JSON.stringify(hiddenNotificationsInStorage));
      } catch (___) {}
    }
    this.setState({hiddenNotifications: hidden});
  };

  onHideAllClick = (event) => {
    event && event.preventDefault();
    const {userNotifications} = this.props;
    userNotifications && userNotifications.hideNotifications();
  };

  onReadAllClick = async () => {
    const {userNotifications} = this.props;
    const request = new ReadAllUserNotifications();
    await request.send();
    if (request.error) {
      message.error(request.error, 5);
    }
    userNotifications.fetch();
  };

  renderSeverityIcon = (notification) => {
    switch (notification.severity) {
      case 'INFO':
        return (
          <Icon
            className="cp-setting-info"
            type="info-circle-o" />
        );
      case 'WARNING':
        return (
          <Icon
            className="cp-setting-warning"
            type="exclamation-circle-o" />
        );
      case 'CRITICAL':
        return (
          <Icon
            className="cp-setting-critical"
            type="close-circle-o" />
        );
      default: return undefined;
    }
  };

  renderControls = () => {
    const {hiddenNotifications} = this.state;
    const hiddenAmount = this.nonBlockingNotifications
      .filter(n => !hiddenNotifications.find(({id}) => id === n.notificationId))
      .length - this.notificationsOnScreen;
    const controlsHidden = !this.userNotificationsEnabled || hiddenAmount <= 0;
    return (
      <div
        style={{
          transition: 'top 0.45s ease-in-out, right 0.45s ease-in-out',
          position: 'fixed',
          right: controlsHidden ? -350 : 0,
          visibility: controlsHidden ? 'hidden' : 'visible',
          pointerEvents: controlsHidden ? 'none' : 'all',
          width: '300px',
          top: this.notificationsBottomBound,
          marginRight: '20px',
          padding: '5px',
          zIndex: 1000
        }}
        className="cp-notification"
      >
        <span>
          {`+${this.unreadCount - this.notificationsOnScreen} notifications`}
        </span>
        <a
          onClick={this.onHideAllClick}
          style={{paddingLeft: '10px'}}
        >
          hide
        </a>
        <a
          onClick={this.onReadAllClick}
          style={{marginLeft: '10px', paddingLeft: '10px'}}
          className={classNames(
            'cp-divider',
            'left'
          )}
        >
          read all
        </a>
      </div>
    );
  };

  openPreviewNotification = (notification) => {
    this.setState({previewNotification: notification}, () => {
      if (notification.type === NOTIFICATION_TYPE.message) {
        this.readMessage(notification, true);
      }
    });
  };

  closePreviewNotification = () => {
    this.setState({previewNotification: null});
  };

  render () {
    const filterBlockingNotification = (notification) => {
      const [state] = this.getHiddenNotifications()
        .filter(n => n.id === notification.notificationId &&
        n.createdDate === notification.createdDate);
      return notification.blocking && !state;
    };
    const blockingNotification = this.allNotifications.filter(filterBlockingNotification)[0];
    return (
      <div id="notification-center" style={{position: 'absolute'}}>
        {
          this.notificationsToShow
            .map((notification, index) => {
              return (
                <SystemNotification
                  {...this.getPositioningInfo(notification, index)}
                  onClose={this.onCloseNotification}
                  onHeightInitialized={this.onHeightInitialized}
                  key={notification.notificationId || notification.createdDate}
                  notification={notification}
                  type={notification.type}
                  onClick={notification.type === NOTIFICATION_TYPE.message
                    ? this.openPreviewNotification
                    : undefined
                  }
                />
              );
            })
        }
        {this.renderControls()}
        <Modal
          title={
            blockingNotification
              ? (
                <Row type="flex" align="middle" className={styles.iconContainer}>
                  {this.renderSeverityIcon(blockingNotification)}
                  {blockingNotification.title}
                </Row>
              )
              : null}
          closable={false}
          footer={
            <Row type="flex" justify="end">
              <Button
                type="primary"
                onClick={() => this.onCloseBlockingNotification(blockingNotification)}
              >
                CONFIRM
              </Button>
            </Row>
          }
          visible={!!blockingNotification}>
          {
            blockingNotification ? (
              <Markdown
                md={blockingNotification.body}
              />
            ) : null
          }
        </Modal>
        {this.state.previewNotification ? (
          <Modal
            onCancel={this.closePreviewNotification}
            footer={false}
            title={(<b>{this.state.previewNotification.title}</b>)}
            visible
          >
            <PreviewNotification
              text={this.state.previewNotification.body}
              sanitize
            />
          </Modal>
        ) : null}
      </div>
    );
  }

  componentDidMount () {
    this.props.notifications.onFetched = this.onFetched;
    if (this.props.delaySeconds) {
      setTimeout(() => {
        this.setState({
          initialized: true
        });
      }, this.props.delaySeconds * 1000);
    } else {
      this.setState({
        initialized: true
      });
    }
    window.addEventListener('resize', this.refreshLayout);
  }

  componentWillUnmount () {
    window.removeEventListener('resize', this.refreshLayout);
    clearTimeout(this.resizeTimeout);
    clearTimeout(this.layoutInfoTimeout);
    clearTimeout(this.fetchNotificationsTimeout);
  }

  fetchUserNotifications = (force = false) => {
    if (this.fetchNotificationsTimeout) {
      clearTimeout(this.fetchNotificationsTimeout);
    }
    if (force) {
      return this.props.userNotifications.fetch()
        .then(() => {
          this.readUserNotifications = 0;
        });
    }
    this.fetchNotificationsTimeout = setTimeout(() => {
      this.props.userNotifications.fetch()
        .then(() => {
          this.readUserNotifications = 0;
        });
    }, 1500);
  };

  refreshLayout = () => {
    if (this.resizeTimeout) {
      clearTimeout(this.resizeTimeout);
    }
    this.resizeTimeout = setTimeout(() => {
      this.setState({
        initialized: true
      });
    }, 500);
  };

  onFetched = (notifications) => {
    const activeNotifications = (notifications.value || []).map(n => n);
    const hiddenNotificationsInStorageStr = localStorage.getItem('hidden_notifications');
    if (hiddenNotificationsInStorageStr) {
      let hiddenNotifications = [];
      try {
        hiddenNotifications = JSON.parse(hiddenNotificationsInStorageStr);
        if (!Array.isArray(hiddenNotifications)) {
          hiddenNotifications = [];
        }
      } catch (___) {}
      const activeHiddenNotifications = hiddenNotifications
        .filter(n => activeNotifications.filter(a => a.notificationId === n.id).length > 0);
      if (hiddenNotifications.length !== activeHiddenNotifications.length) {
        try {
          localStorage.setItem('hidden_notifications', JSON.stringify(activeHiddenNotifications));
        } catch (___) {}
      }
    }
  };
}

export {
  PredefinedNotifications,
  NOTIFICATION_TYPE
};
