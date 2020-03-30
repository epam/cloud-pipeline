/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {
  Button, Card,
  Checkbox,
  Icon,
  message,
  Modal,
  Row,
  Table,
  Tabs
} from 'antd';
import styles from './styles.css';
import PipelineGitCredentials from '../../models/pipelines/PipelineGitCredentials';
import Notifications from '../../models/notifications/Notifications';
import UpdateNotification from '../../models/notifications/UpdateNotification';
import DeleteNotification from '../../models/notifications/DeleteNotification';
import EditSystemNotificationForm from './forms/EditSystemNotificationForm';
import UserManagementForm from './UserManagementForm';
import EmailNotificationSettings from './EmailNotificationSettings';
import AWSRegionsForm from './AWSRegionsForm';
import CLIForm from './CLIForm';
import Preferences from './Preferences';
import SystemLogs from './SystemLogs';
import displayDate from '../../utils/displayDate';
import 'highlight.js/styles/github.css';

@inject(({authenticatedUserInfo, preferences}) => ({
  authenticatedUserInfo,
  preferences,
  notifications: new Notifications(),
  pipelineGitCredentials: new PipelineGitCredentials()
}))
@observer
export default class Index extends React.Component {
  state = {
    notification: {
      updateNotification: null,
      createNotification: false
    },
    activeKey: 'cli',
    operationSystems: null
  };

  onOkClicked = () => {
    const close = () => {
      this.emailNotificationSettingsForm && this.emailNotificationSettingsForm.reload(true);
      this.preferencesForm && this.preferencesForm.reload(true);
      this.awsRegionsForm && this.awsRegionsForm.reload(true);
      this.setState({activeKey: 'cli'});
    };
    if (
      (this.emailNotificationSettingsForm && this.emailNotificationSettingsForm.templateModified) ||
      (this.preferencesForm && this.preferencesForm.templateModified) ||
      (this.awsRegionsForm && this.awsRegionsForm.regionModified)
    ) {
      Modal.confirm({
        title: 'You have unsaved changes. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        async onOk () {
          close();
        },
        okText: 'Yes',
        cancelText: 'No'
      });
    } else {
      close();
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

  updateNotification = async (notification) => {
    const hide = message.loading('Updating notification...', 0);
    const request = new UpdateNotification();
    await request.send(notification);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.notifications.fetch();
      hide();
      this.closeUpdateNotificationForm();
      this.closeCreateNotificationForm();
    }
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

  renderSystemEvents = () => {
    const data = (
      this.props.notifications.loaded
        ? (this.props.notifications.value || []).map(n => n)
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
      this.props.notifications.fetch();
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
      <Row>
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
      </Row>
    );
  };

  isAdmin = () => {
    if (!this.props.authenticatedUserInfo.loaded) {
      return false;
    }
    return this.props.authenticatedUserInfo.value.admin;
  };

  managementForm;
  emailNotificationSettingsForm;
  preferencesForm;
  awsRegionsForm;

  initializeManagementForm = (form) => {
    if (form) {
      this.managementForm = form;
    }
  };

  initializeEmailNotificationSettingsForm = (form) => {
    if (form && form.wrappedInstance) {
      this.emailNotificationSettingsForm = form.wrappedInstance;
    }
  };

  initializePreferencesForm = (form) => {
    if (form && form.wrappedInstance) {
      this.preferencesForm = form.wrappedInstance;
    }
  };

  initializeAWSRegionsForm = (form) => {
    if (form) {
      this.awsRegionsForm = form;
    }
  };

  getTabs = () => {
    const tabs = [];
    tabs.push(
      <Tabs.TabPane tab="CLI" key="cli">
        <CLIForm />
      </Tabs.TabPane>
    );
    if (this.isAdmin()) {
      tabs.push(
        <Tabs.TabPane tab="System events" key="system events">
          {this.renderSystemEvents()}
        </Tabs.TabPane>
      );
      tabs.push(
        <Tabs.TabPane tab="User management" key="user management">
          <UserManagementForm
            onInitialized={this.initializeManagementForm}
            isAdmin={this.isAdmin}
          />
        </Tabs.TabPane>
      );
      tabs.push(
        <Tabs.TabPane tab="Email notifications" key="email notifications">
          <EmailNotificationSettings ref={this.initializeEmailNotificationSettingsForm} />
        </Tabs.TabPane>
      );
      tabs.push(
        <Tabs.TabPane tab="Preferences" key="preferences">
          <Preferences ref={this.initializePreferencesForm} />
        </Tabs.TabPane>
      );
      tabs.push(
        <Tabs.TabPane tab="Cloud Regions" key="cloud regions">
          <AWSRegionsForm
            onInitialize={this.initializeAWSRegionsForm} />
        </Tabs.TabPane>
      );
      tabs.push(
        <Tabs.TabPane tab="System Logs" key="system logs">
          <SystemLogs />
        </Tabs.TabPane>
      );
    }
    return tabs;
  };

  onTabChanged = (key) => {
    const change = () => {
      this.setState({
        activeKey: key
      }, () => {
        if (key === 'user management' && this.managementForm) {
          this.managementForm.reload();
        }
        if (key === 'email notifications' && this.emailNotificationSettingsForm) {
          this.emailNotificationSettingsForm.reload(true);
        }
        if (key === 'preferences' && this.preferencesForm) {
          this.preferencesForm.reload(true);
        }
        if (key === 'cloud regions' && this.awsRegionsForm) {
          this.awsRegionsForm.reload(true);
        }
      });
    };
    if ((this.state.activeKey === 'email notifications' &&
      this.emailNotificationSettingsForm &&
      this.emailNotificationSettingsForm.templateModified) ||
      (this.state.activeKey === 'preferences' &&
      this.preferencesForm &&
      this.preferencesForm.templateModified) ||
      (this.state.activeKey === 'cloud regions' &&
      this.awsRegionsForm &&
      this.awsRegionsForm.regionModified)) {
      Modal.confirm({
        title: 'You have unsaved changes. Continue?',
        style: {
          wordWrap: 'break-word'
        },
        async onOk () {
          change();
        },
        okText: 'Yes',
        cancelText: 'No'
      });
    } else {
      change();
    }
  };

  render () {
    return (
      <Card
        id="settings-container"
        style={{overflowY: 'auto'}}
        className={styles.container}
        bodyStyle={{padding: 5}}>
        <Tabs
          className="settings-tabs"
          activeKey={this.state.activeKey}
          onChange={this.onTabChanged}>
          {this.getTabs()}
        </Tabs>
      </Card>
    );
  }
}
