import React, {Component} from 'react';
import {inject, observer} from 'mobx-react';
import {
  Button,
  Checkbox,
  Icon,
  message,
  Row,
  Modal,
  Table
} from 'antd';
import EditSystemNotificationForm from './forms/EditSystemNotificationForm';
import Notifications from '../../models/notifications/Notifications';
import UpdateNotification from '../../models/notifications/UpdateNotification';
import DeleteNotification from '../../models/notifications/DeleteNotification';
import displayDate from '../../utils/displayDate';
import styles from './styles.css';

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
      onOk () {
        return deleteNotification();
      }
    });
  };

  renderSeverityIcon = (notification) => {
    switch (notification.severity) {
      case 'INFO':
        return (
          <Icon
            style={{fontSize: 'larger'}}
            className={styles[notification.severity.toLowerCase()]}
            type="info-circle-o" />
        );
      case 'WARNING':
        return (
          <Icon
            style={{fontSize: 'larger'}}
            className={styles[notification.severity.toLowerCase()]}
            type="exclamation-circle-o" />
        );
      case 'CRITICAL':
        return (
          <Icon
            style={{fontSize: 'larger'}}
            className={styles[notification.severity.toLowerCase()]}
            type="close-circle-o" />
        );
      default: return undefined;
    }
  };

  render () {
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
            <Row>
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
                <Icon type="edit" />
              </Button>
              <Button
                id="delete-notification-button"
                size="small"
                type="danger"
                onClick={() => this.deleteNotificationConfirm(notification)}>
                <Icon type="delete" />
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
              <Row type="flex" justify="end">
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
                  <Icon type="plus" /> ADD
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
};
