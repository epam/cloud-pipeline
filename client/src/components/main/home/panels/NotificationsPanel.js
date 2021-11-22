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
import LoadingView from '../../../special/LoadingView';
import {Alert, Card, Col, Icon, Row} from 'antd';
import displayDate from '../../../../utils/displayDate';
import Markdown from '../../../special/markdown';
import styles from './Panel.css';

@inject('notifications')
@observer
export default class NotificationsPanel extends React.Component {

  static propTypes = {
    onInitialize: PropTypes.func
  };

  @computed
  get notifications () {
    if (this.props.notifications.loaded) {
      return (this.props.notifications.value || []).map(n => n);
    }
    return [];
  }

  renderSeverityIcon = (notification) => {
    switch (notification.severity) {
      case 'INFO':
        return (
          <Icon
            className="cp-notification-status-info"
            type="info-circle-o" />
        );
      case 'WARNING':
        return (
          <Icon
            className="cp-notification-status-warning"
            type="exclamation-circle-o" />
        );
      case 'CRITICAL':
        return (
          <Icon
            className="cp-notification-status-critical"
            type="close-circle-o" />
        );
      default: return undefined;
    }
  };

  renderNotification = (notification) => {
    return (
      <Row type="flex" style={{padding: '5px 0 0 5px'}}>
        <Col className={styles.iconColumn}>
          {this.renderSeverityIcon(notification)}
        </Col>
        <Col style={{paddingLeft: 10, flex: 1, wordBreak: 'break-word'}}>
          <Row type="flex" style={{fontWeight: 'bold', fontSize: 'larger'}}>
            {notification.title}
          </Row>
          <Row type="flex">
            <Markdown
              md={notification.body}
              style={{margin: '10px 0', fontSize: 'smaller'}}
            />
          </Row>
          <Row
            type="flex"
            style={{fontSize: 'x-small'}}
            className="cp-text-not-important"
          >
            {displayDate(notification.createdDate)}
          </Row>
        </Col>
      </Row>
    );
  };

  render () {
    if (!this.props.notifications.loaded && this.props.notifications.pending) {
      return <LoadingView />;
    }
    if (this.props.notifications.error) {
      return <Alert type="warning" message={this.props.pipelinesLibrary.error} />;
    }
    if (this.notifications.length === 0) {
      return (
        <div className={styles.container}>
          There are no system notifications.
        </div>
      );
    }
    return (
      <div className={styles.container}>
        {
          this.notifications.map((notification, index) => {
            return (
              <Card
                key={index}
                bodyStyle={{padding: 2}}
                className="cp-panel-card"
              >
                {this.renderNotification(notification)}
              </Card>
            );
          })
        }
      </div>
    );
  }

  update () {
    this.forceUpdate();
  }

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
  }
}
