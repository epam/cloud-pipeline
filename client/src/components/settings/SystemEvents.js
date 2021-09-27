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

import React, {Component} from 'react';
import {inject, observer} from 'mobx-react';

import {
  CloseCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
  PlusOutlined
} from '@ant-design/icons';

import {Button, Checkbox, message, Row, Modal, Table, Alert} from 'antd';
import EditSystemNotificationForm from './forms/EditSystemNotificationForm';
import Notifications from '../../models/notifications/Notifications';
import UpdateNotification from '../../models/notifications/UpdateNotification';
import DeleteNotification from '../../models/notifications/DeleteNotification';
import displayDate from '../../utils/displayDate';
import styles from './styles.css';
import {withRouter} from 'react-router-dom';

@withRouter
@inject(({authenticatedUserInfo}) => ({
  authenticatedUserInfo,
  notifications: new Notifications()
}))
@observer
export default class SystemEvents extends Component {
  state = {
    notification: {
      updateNotification: null,
      createNotification: false
    }
  };

  updateNotification = async (notification) => {
    const {notifications} = this.props;
    const hide = message.loading('Updating notification...', 0);
    const request = new UpdateNotification();
    await request.send(notification);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await notifications.fetch();
      hide();
      this.closeUpdateNotificationForm();
      this.closeCreateNotificationForm();
    }
  };

  openCreateNotificationForm = () => {
    const notificationState = this.state.notification;
    notificationState.createNotification = true;
    this.setState({notification: notificationState});
  };

  closeCreateNotificationForm = () => {
    const notificationState = this.state.notification;
    notificationState.createNotification = false;
    this.setState({notification: notificationState});
  };

  openUpdateNotificationForm = (notification) => {
    const notificationState = this.state.notification;
    notificationState.updateNotification = notification;
    this.setState({notification: notificationState});
  };

  closeUpdateNotificationForm = () => {
    const notificationState = this.state.notification;
    notificationState.updateNotification = null;
    this.setState({notification: notificationState});
  };

  deleteNotificationConfirm = (notification) => {
    const deleteNotification = async () => {
      const hide = message.loading('Removing notification...', 0);
      const request = new DeleteNotification(notification.notificationId);
      await request.fetch();
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.props.notifications.fetch();
        hide();
      }
    };
    Modal.confirm({
      title: `Are you sure you want to delete notification '${notification.title}'?`,
      style: {
        wordWrap: 'break-word'
      },
      okType: 'danger',
      onOk () {
        return deleteNotification();
      }
    });
  };

  renderSeverityIcon = (notification) => {
    switch (notification.severity) {
      case 'INFO':
        return (
          <InfoCircleOutlined
            style={{fontSize: 'larger', marginRight: '5px'}}
            className={styles[notification.severity.toLowerCase()]} />
        );
      case 'WARNING':
        return (
          <ExclamationCircleOutlined
            style={{fontSize: 'larger', marginRight: '5px'}}
            className={styles[notification.severity.toLowerCase()]} />
        );
      case 'CRITICAL':
        return (
          <CloseCircleOutlined
            style={{fontSize: 'larger', marginRight: '5px'}}
            className={styles[notification.severity.toLowerCase()]} />
        );
      default: return undefined;
    }
  };

  render () {
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return null;
    }
    if (!this.props.authenticatedUserInfo.value.admin) {
      return (
        <Alert type="error" message="Access is denied" />
      );
    }
    const {notifications} = this.props;
    const data = (
      notifications.loaded
        ? (notifications.value || []).map(n => n)
        : []
    ).sort(
      (a, b) => {
        if (a.title > b.title) {
          return 1;
        } else if (a.title < b.title) {
          return -1;
        }
        return 0;
      }
    );
    const refreshTable = () => {
      notifications.fetch();
    };
    const onChangeState = (notification) => {
      if (notification.state === 'ACTIVE') {
        notification.state = 'INACTIVE';
      } else {
        notification.state = 'ACTIVE';
      }
      this.updateNotification(notification);
    };
    const onChangeBlocking = (notification) => {
      notification.blocking = !notification.blocking;
      this.updateNotification(notification);
    };
    const columns = [
      {
        dataIndex: 'title',
        key: 'title',
        className: 'notification-title-column',
        render: (title, notification) => {
          return (
            <Row align="middle">
              {this.renderSeverityIcon(notification)} {title}
            </Row>
          );
        }
      },
      {
        dataIndex: 'createdDate',
        key: 'createdDate',
        className: 'notification-created-date-column',
        render: (createdDate) => displayDate(createdDate)
      },
      {
        dataIndex: 'state',
        key: 'state',
        className: 'notification-status-column',
        render: (state, notification) => {
          return (
            <Checkbox
              id="change-notification-status-checkbox"
              checked={state === 'ACTIVE'}
              onChange={() => onChangeState(notification)}>
              Active
            </Checkbox>
          );
        }
      },
      {
        dataIndex: 'blocking',
        key: 'blocking',
        className: 'notification-blocking-column',
        render: (blocking, notification) => {
          return (
            <Checkbox
              id="change-notification-blocking-checkbox"
              checked={blocking}
              onChange={() => onChangeBlocking(notification)}>
              Blocking
            </Checkbox>
          );
        }
      },
      {
        key: 'actions',
        className: styles.actions,
        render: (notification) => {
          return (
            <Row type="flex" justify="end">
              <Button
                id="edit-notification-button"
                size="small"
                onClick={() => this.openUpdateNotificationForm(notification)}>
                <EditOutlined />
              </Button>
              <Button
                id="delete-notification-button"
                size="small"
                danger
                onClick={() => this.deleteNotificationConfirm(notification)}>
                <DeleteOutlined />
              </Button>
            </Row>
          );
        }
      }
    ];
    return (
      <div style={{flex: 1, minHeight: 0}}>
        <Table
          id="notifications-table"
          className={styles.table}
          rowClassName={notification => `notification-${notification.notificationId}-row`}
          title={() => {
            return (
              <Row
                justify="end"
                align="middle"
              >
                <Button
                  id="refresh-notifications-button"
                  size="small"
                  onClick={refreshTable}
                  style={{marginRight: 5}}>
                  Refresh
                </Button>
                <Button
                  id="add-notification-button"
                  size="small"
                  onClick={this.openCreateNotificationForm}>
                  <PlusOutlined /> ADD
                </Button>
              </Row>
            );
          }}
          showHeader={false}
          rowKey="notificationId"
          loading={this.props.notifications.pending}
          columns={columns}
          dataSource={data}
          expandedRowClassName={
            notification => `notification-${notification.notificationId}-expanded-row`
          }
          expandedRowRender={
            notification =>
              (
                <p
                  className={`notification-${notification.notificationId}-body`}
                >
                  {notification.body}
                </p>
              )
          }
          size="small" />
        <EditSystemNotificationForm
          pending={false}
          notification={this.state.notification.updateNotification}
          visible={!!this.state.notification.updateNotification}
          onCancel={this.closeUpdateNotificationForm}
          onSubmit={this.updateNotification} />
        <EditSystemNotificationForm
          pending={false}
          visible={this.state.notification.createNotification}
          onCancel={this.closeCreateNotificationForm}
          onSubmit={this.updateNotification} />
      </div>
    );
  }
}
