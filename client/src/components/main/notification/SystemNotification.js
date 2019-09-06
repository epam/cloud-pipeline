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
import {observer} from 'mobx-react';
import PropTypes from 'prop-types';
import {Row, Icon} from 'antd';
import NotificationView from '../../special/notifications/controls/NotificationView';
import styles from './SystemNotification.css';
import displayDate from '../../../utils/displayDate';

@observer
export default class SystemNotification extends React.Component {

  static margin = 10;

  state = {
    initialized: false,
    height: undefined,
    container: undefined
  };

  static propTypes = {
    notification: PropTypes.shape({
      notificationId: PropTypes.oneOfType([
        PropTypes.string,
        PropTypes.number
      ]),
      title: PropTypes.string,
      body: PropTypes.string,
      severity: PropTypes.string,
      createdDate: PropTypes.string
    }),
    onHeightInitialized: PropTypes.func,
    visible: PropTypes.bool,
    top: PropTypes.number,
    onClose: PropTypes.func
  };

  changeHeight = (div) => {
    this.setState({
      height: div.offsetHeight,
      initialized: true,
      container: div
    }, () => {
      if (this.props.onHeightInitialized) {
        this.props.onHeightInitialized(this.props.notification, this.state.height);
      }
      this.changePosition();
    });
  };

  onInitialized = (div) => {
    if (div && (!this.state.initialized || this.state.height !== div.offsetHeight)) {
      this.changeHeight(div);
    }
  };

  display = (top, delay) => {
    if (delay) {
      setTimeout(() => this.display(top), delay);
    } else {
      if (this.state.container) {
        this.state.container.style.top = `${top}px`;
        this.state.container.style.right = '0px';
      }
    }
  };

  hide = () => {
    if (this.state.container) {
      this.state.container.style.right = '-350px';
    }
  };

  onClose = () => {
    if (this.props.onClose) {
      this.props.onClose(this.props.notification);
    }
  };

  renderSeverityIcon = () => {
    switch (this.props.notification.severity) {
      case 'INFO':
        return (
          <Icon
            className={styles[this.props.notification.severity.toLowerCase()]}
            type="info-circle-o" />
        );
      case 'WARNING':
        return (
          <Icon
            className={styles[this.props.notification.severity.toLowerCase()]}
            type="exclamation-circle-o" />
        );
      case 'CRITICAL':
        return (
          <Icon
            className={styles[this.props.notification.severity.toLowerCase()]}
            type="close-circle-o" />
        );
      default: return undefined;
    }
  };

  render () {
    return (
      <div
        id={`notification-${this.props.notification.notificationId}`}
        ref={this.onInitialized}
        className={`${styles.notification} ${styles.container} ${styles[this.props.notification.severity.toLowerCase()]}`}
        style={{right: -350, top: SystemNotification.margin, display: 'flex', flexDirection: 'row'}}>
        <div className={styles.iconColumn}>
          {this.renderSeverityIcon()}
        </div>
        <Row type="flex" style={{flex: 1, display: 'flex', flexDirection: 'column', wordBreak: 'break-word'}}>
          <Row type="flex" style={{
            marginBottom: 5,
            display: 'flex',
            flexDirection: 'row'
          }}>
            <span className={styles.title} style={{flex: 1}}>{this.props.notification.title}</span>
            <Icon
              id="notification-close-button"
              type="close"
              onClick={this.onClose}
              style={{cursor: 'pointer', marginLeft: 5, marginTop: 5}} />
          </Row>
          <Row>
            <span className={styles.body}>
              <NotificationView
                text={this.props.notification.body}
              />
            </span>
          </Row>
          <Row type="flex" justify="end">
            <span className={styles.date}>{displayDate(this.props.notification.createdDate)}</span>
          </Row>
        </Row>
      </div>
    );
  }

  changePosition = () => {
    if (!this.props.visible) {
      this.hide();
    } else {
      this.display(this.props.top);
    }
  };

  componentDidUpdate (prevProps) {
    if (prevProps.visible !== this.props.visible || prevProps.top !== this.props.top) {
      this.changePosition();
    }
    if (this.state.container && this.state.container.offsetHeight !== this.state.height) {
      this.changeHeight(this.state.container);
    }
  }

  componentDidMount () {
    this.changePosition();
  }
}
