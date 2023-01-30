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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import moment from 'moment-timezone';
import {
  Button,
  Pagination,
  Icon,
  Checkbox,
  Modal,
  message
} from 'antd';
import classNames from 'classnames';
import ReadMessage from '../../../models/notifications/ReadMessage';
import displayDate from '../../../utils/displayDate';
import PreviewNotification from './PreviewNotification';
import styles from './NotificationBrowser.css';

const PAGE_SIZE = 10;

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

@inject('messages')
@observer
export default class NotificationBrowser extends React.Component {
  state = {
    currentPage: 1,
    unreadOnly: false,
    previewNotification: null
  }

  @computed
  get messages () {
    const {messages} = this.props;
    if (!messages || !messages.loaded) {
      return [];
    }
    return [...(messages.value || [])].sort(dateSorter);
  }

  @computed
  get filteredMessages () {
    const {unreadOnly} = this.state;
    if (unreadOnly) {
      return this.unreadMessages;
    }
    return this.messages;
  }

  @computed
  get unreadMessages () {
    return this.messages.filter(message => !message.isRead);
  }

  changeUnreadOnlyFilter = (event) => {
    this.setState({
      unreadOnly: event.target.checked
    });
  };

  previewNotification = (notification) => {
    this.setState({previewNotification: notification});
  };

  onPageChange = page => this.setState({currentPage: page});

  readNotification = async (notification) => {
    if (!notification.isRead) {
      const {messages} = this.props;
      const request = new ReadMessage();
      const payload = {...notification};
      payload.isRead = true;
      payload.readDate = moment.utc().format('YYYY-MM-DD HH:mm:ss.SSS');
      await request.send(payload);
      if (request.error) {
        message.error(request.error, 5);
      } else {
        messages.fetch();
      }
    }
    this.setState({previewNotification: null});
  };

  readAllNotifications = () => {
    // todo: wait for API
  };

  renderHeader = () => {
    return (
      <div
        className={classNames(
          styles.notificationGridRow,
          'cp-divider',
          'bottom'
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
    const {currentPage, unreadOnly} = this.state;
    const slicedData = this.filteredMessages
      .slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
    const emptyPlaceholder = (
      <div className={styles.emptyPlaceholder}>
        {unreadOnly ? 'No new notifications' : 'Notifications not found'}
      </div>
    );
    return (
      <div className={styles.notificationsContainer}>
        {this.renderHeader()}
        {slicedData.length > 0 ? slicedData.map(notification => (
          <div
            className={classNames(
              styles.notificationGridRow,
              'cp-divider',
              'bottom',
              {'cp-disabled': notification.isRead}
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
        )) : emptyPlaceholder}
      </div>
    );
  };

  render () {
    const {
      currentPage,
      unreadOnly,
      previewNotification
    } = this.state;
    return (
      <div className={styles.container}>
        <b className={styles.title}>
          Notifications list
        </b>
        <div className={styles.controlsRow}>
          <Button
            onClick={this.readAllNotifications}
            disabled={this.unreadMessages.length === 0}
            size="small"
            style={{marginRight: '15px'}}
          >
            Read all
          </Button>
          <Checkbox
            checked={unreadOnly}
            onChange={this.changeUnreadOnlyFilter}
          >
            Show only unread messages
          </Checkbox>
        </div>
        {this.renderNotifications()}
        <Pagination
          className={styles.pagination}
          current={currentPage}
          onChange={this.onPageChange}
          total={this.filteredMessages.length}
          pageSize={PAGE_SIZE}
          size="small"
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
