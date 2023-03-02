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
import {observer, inject} from 'mobx-react';
import {computed, observable} from 'mobx';
import moment from 'moment-timezone';
import {
  Button,
  Pagination,
  Icon,
  Modal,
  message,
  Select,
  Spin
} from 'antd';
import classNames from 'classnames';
import ReadMessage from '../../../models/notifications/ReadMessage';
import NotificationsRequest from '../../../models/notifications/CurrentUserNotificationsPaging';
import ReadAllUserNotifications from '../../../models/notifications/ReadAllUserNotifications';
import displayDate from '../../../utils/displayDate';
import PreviewNotification from './PreviewNotification';
import styles from './NotificationBrowser.css';

const PAGE_SIZE = 20;

const MODES = {
  read: 'Read messages',
  new: 'New messages'
};

function dateSorter (a, b) {
  const dateA = moment.utc(a.createdDate);
  const dateB = moment.utc(b.createdDate);
  if (dateA === dateB) {
    return 0;
  } else if (dateA > dateB) {
    return -1;
  } else {
    return 1;
  }
};

@inject('userNotifications')
@observer
export default class NotificationBrowser extends React.Component {
  state = {
    currentPage: 0,
    previewNotification: null,
    pending: false,
    mode: MODES.new
  }

  @observable
  _notifications;

  componentDidMount () {
    const {currentPage, mode} = this.state;
    this.fetchPage(currentPage, mode === MODES.read);
  }

  @computed
  get notifications () {
    if (!this._notifications) {
      return [];
    }
    return [...(this._notifications.elements || [])].sort(dateSorter);
  }

  @computed
  get totalNotifications () {
    if (!this._notifications) {
      return 0;
    }
    return this._notifications.totalCount;
  }

  changeMode = (description) => {
    if (this.state.mode === description) {
      return;
    }
    this.setState({
      currentPage: 0,
      mode: description
    }, () => {
      const {mode, currentPage} = this.state;
      this.fetchPage(currentPage, mode === MODES.read);
    });
  };

  previewNotification = (notification) => {
    this.setState({previewNotification: notification});
  };

  onPageChange = page => {
    this.setState({currentPage: page - 1}, () => {
      const {currentPage, mode} = this.state;
      this.fetchPage(currentPage, mode === MODES.read);
    });
  };

  readNotification = (notification) => {
    if (!notification.isRead) {
      this.setState({pending: true}, async () => {
        const {currentPage, mode} = this.state;
        const {userNotifications} = this.props;
        const request = new ReadMessage();
        const payload = {...notification};
        payload.isRead = true;
        payload.readDate = moment.utc().format('YYYY-MM-DD HH:mm:ss.SSS');
        await request.send(payload);
        if (request.error) {
          message.error(request.error, 5);
        } else {
          this.fetchPage(currentPage, mode === MODES.read);
          userNotifications.fetch();
        }
      });
    }
    this.setState({previewNotification: null});
  };

  readAllNotifications = () => {
    this.setState({pending: true}, async () => {
      const {userNotifications} = this.props;
      const {currentPage, mode} = this.state;
      const request = new ReadAllUserNotifications();
      await request.send();
      if (request.error) {
        message.error(request.error, 5);
      }
      this.fetchPage(currentPage, mode === MODES.read);
      userNotifications.fetch();
    });
  };

  fetchPage = (page, isRead) => {
    this.setState({pending: true}, async () => {
      const request = new NotificationsRequest(
        page,
        PAGE_SIZE,
        isRead
      );
      await request.fetch();
      if (request.error) {
        message.error(request.error, 5);
      }
      this._notifications = request.value;
      this.setState({pending: false});
    });
  };

  refreshPage = () => {
    const {currentPage, mode} = this.state;
    this.fetchPage(currentPage, mode === MODES.read);
  };

  renderHeader = () => {
    return (
      <div
        className={classNames(
          styles.notificationGridRow,
          styles.header,
          'cp-divider',
          'bottom',
          'cp-card-background-color'
        )}
      >
        <div className={classNames(
          styles.notificationCell,
          styles.notificationStatus,
          styles.header
        )}>
          Status
        </div>
        <div className={classNames(
          styles.notificationCell,
          styles.notificationTitle,
          styles.header
        )}>
          Title
        </div>
        <div className={classNames(
          styles.notificationCell,
          styles.notificationBody,
          styles.header
        )}>
          Text
        </div>
        <div className={classNames(
          styles.notificationCell,
          styles.notificationDate,
          styles.header
        )}>
          Created date
        </div>
        <div className={classNames(
          styles.notificationCell,
          styles.notificationReadDate,
          styles.header
        )}>
          Read date
        </div>
      </div>
    );
  };

  renderNotifications = () => {
    const {mode, pending} = this.state;
    const emptyPlaceholder = (
      <div className={styles.emptyPlaceholder}>
        {MODES[mode] === MODES.new
          ? 'Notifications not found'
          : 'No new notifications'
        }
      </div>
    );
    return (
      <div className={styles.notificationsContainer}>
        {this.renderHeader()}
        <div>
          <Spin spinning={pending}>
            {this.notifications.length > 0
              ? this.notifications.map(notification => (
                <div
                  className={classNames(
                    styles.notificationGridRow,
                    'cp-divider',
                    'bottom',
                    {
                      'cp-table-element-dimmed': notification.isRead,
                      'cp-table-element': !notification.isRead
                    }
                  )}
                  key={notification.id}
                  onClick={() => this.previewNotification(notification)}
                >
                  <div className={classNames(
                    styles.notificationCell,
                    styles.notificationStatus
                  )}>
                    <Icon
                      className={notification.isRead
                        ? 'cp-disabled'
                        : 'cp-setting-message'
                      }
                      type="mail"
                    />
                  </div>
                  <b className={classNames(
                    styles.notificationCell,
                    styles.notificationTitle
                  )}>
                    {notification.subject}
                  </b>
                  <div className={classNames(
                    styles.notificationCell,
                    styles.notificationBody
                  )}>
                    <PreviewNotification
                      text={notification.text}
                      sanitize
                      className={styles.mdPreviewEllipsis}
                    />
                  </div>
                  <div className={classNames(
                    styles.notificationCell,
                    styles.notificationDate
                  )}>
                    {displayDate(notification.createdDate, 'YYYY-MM-DD HH:mm:ss')}
                  </div>
                  <div className={classNames(
                    styles.notificationCell,
                    styles.notificationReadDate
                  )}>
                    {displayDate(notification.readDate, 'YYYY-MM-DD HH:mm:ss')}
                  </div>
                </div>
              ))
              : emptyPlaceholder
            }
          </Spin>
        </div>
      </div>
    );
  };

  render () {
    const {
      currentPage,
      mode,
      previewNotification,
      pending
    } = this.state;
    return (
      <div
        className={
          classNames(
            styles.container,
            'cp-panel',
            'cp-panel-no-hover',
            'cp-panel-borderless'
          )
        }
      >
        <div className={styles.header}>
          <div>
            <b className={styles.title}>
              Notifications list
            </b>
            <Select
              value={mode}
              style={{width: 130}}
              onChange={this.changeMode}
              size="small"
              disabled={pending}
              className={styles.control}
            >
              {Object.values(MODES).map((description) => (
                <Select.Option
                  value={description}
                  key={description}
                >
                  {description}
                </Select.Option>
              ))}
            </Select>
          </div>
          <div>
            <Button
              onClick={this.readAllNotifications}
              size="small"
              disabled={pending}
              className={styles.control}
              style={{padding: '0 15px'}}
            >
              Read all
            </Button>
            <Button
              size="small"
              onClick={this.refreshPage}
              disabled={pending}
              className={styles.control}
              style={{padding: '0 15px'}}
            >
              Refresh
            </Button>
          </div>
        </div>
        {this.renderNotifications()}
        <Pagination
          className={styles.pagination}
          current={currentPage + 1}
          onChange={this.onPageChange}
          total={this.totalNotifications}
          pageSize={PAGE_SIZE}
          size="small"
          disabled={pending}
        />
        {previewNotification ? (
          <Modal
            onCancel={() => this.readNotification(previewNotification)}
            footer={false}
            title={(<b>{previewNotification.subject}</b>)}
            visible
          >
            <PreviewNotification
              text={previewNotification.text}
              sanitize
            />
          </Modal>
        ) : null}
      </div>
    );
  }
}
