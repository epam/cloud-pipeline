/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import SystemNotification from './SystemNotification';
import {message, Modal, Button, Row, Icon} from 'antd';
import moment from 'moment';
import NotificationView from '../../special/notifications/controls/NotificationView';
import ConfirmNotification from '../../../models/notifications/ConfirmNotification';
import styles from './SystemNotification.css';

@inject(({notifications}) => ({
  notifications
}))
@observer
export default class NotificationCenter extends React.Component {

  static propTypes = {
    delaySeconds: PropTypes.number
  };

  state = {
    notificationsState: [],
    hiddenNotifications: [],
    initialized: false
  };

  @computed
  get notifications () {
    if (!this.props.notifications.loaded || !this.state.initialized) {
      return [];
    }
    return (this.props.notifications.value || []).sort((a, b) => {
      const dateA = moment.utc(a.createdDate);
      const dateB = moment.utc(b.createdDate);
      if (dateA > dateB) {
        return -1;
      } else if (dateA < dateB) {
        return 1;
      }
      return 0;
    });
  }

  @computed
  get nonBlockingNotifications () {
    return this.notifications.filter(n => !n.blocking);
  }

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
    const [state] = this.getHiddenNotifications()
      .filter(n => n.id === notification.notificationId &&
      n.createdDate === notification.createdDate);
    const [infoState] = this.state.notificationsState
      .filter(n => n.id === notification.notificationId && n.height !== undefined);
    return !state && !!infoState;
  };

  getPositioningInfo = (notification, index) => {
    if (this.notificationIsVisible(notification)) {
      let top = SystemNotification.margin;
      const notifications = this.notifications;
      for (let i = 0; i < index; i++) {
        const notificationItem = notifications[i];
        const [state] = this.state.notificationsState
          .filter(n => n.id === notificationItem.notificationId);
        if (this.notificationIsVisible(notificationItem) && state && state.height !== undefined) {
          top += state.height + SystemNotification.margin;
        }
      }
      return {
        top,
        visible: true
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

  onCloseNotification = (notification) => {
    const hidden = this.state.hiddenNotifications;
    hidden.push({
      id: notification.notificationId,
      createdDate: notification.createdDate
    });
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

  renderSeverityIcon = (notification) => {
    switch (notification.severity) {
      case 'INFO':
        return (
          <Icon
            className={styles[notification.severity.toLowerCase()]}
            type="info-circle-o" />
        );
      case 'WARNING':
        return (
          <Icon
            className={styles[notification.severity.toLowerCase()]}
            type="exclamation-circle-o" />
        );
      case 'CRITICAL':
        return (
          <Icon
            className={styles[notification.severity.toLowerCase()]}
            type="close-circle-o" />
        );
      default: return undefined;
    }
  };

  render () {
    const filterBlockingNotification = (notification) => {
      const [state] = this.getHiddenNotifications()
        .filter(n => n.id === notification.notificationId &&
        n.createdDate === notification.createdDate);
      return notification.blocking && !state;
    };
    const blockingNotification = this.notifications.filter(filterBlockingNotification)[0];
    return (
      <div id="notification-center" style={{position: 'absolute'}}>
        {
          this.nonBlockingNotifications
            .map((notification, index) => {
              return (
                <SystemNotification
                  {...this.getPositioningInfo(notification, index)}
                  onClose={this.onCloseNotification}
                  onHeightInitialized={this.onHeightInitialized}
                  key={notification.notificationId}
                  notification={notification} />
            );
          })
        }
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
              <Button type="primary" onClick={() => this.onCloseBlockingNotification(blockingNotification)}>
                CONFIRM
              </Button>
            </Row>
          }
          visible={!!blockingNotification}>
          {
            blockingNotification ? (
              <NotificationView
                text={blockingNotification.body}
              />
            ) : null
          }
        </Modal>
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
  }

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
