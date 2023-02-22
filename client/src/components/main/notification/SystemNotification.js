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
import classNames from 'classnames';
import displayDate from '../../../utils/displayDate';
import PreviewNotification from './PreviewNotification';
import {NOTIFICATION_TYPE} from './NotificationCenter';
import styles from './SystemNotification.css';

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
    onClose: PropTypes.func,
    onClick: PropTypes.func
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

  onClose = (event) => {
    event && event.stopPropagation();
    if (this.props.onClose) {
      this.props.onClose(this.props.notification);
    }
  };

  onClick = (event) => {
    const {onClick, notification} = this.props;
    if (!/^a$/i.test(event.target.tagName)) {
      onClick && onClick(notification);
    }
  };

  renderSeverityIcon = () => {
    if (this.props.notification.type === NOTIFICATION_TYPE.message) {
      return (
        <Icon
          className="cp-setting-message"
          type="mail" />
      );
    }
    switch (this.props.notification.severity) {
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

  render () {
    const {visible} = this.props;
    return (
      <div
        id={`notification-${this.props.notification.notificationId}`}
        ref={this.onInitialized}
        className={
          classNames(
            styles.notification,
            styles.container,
            'cp-notification'
          )
        }
        style={{
          right: visible ? 0 : -350,
          top: visible
            ? top
            : window.innerHeight,
          display: 'flex',
          flexDirection: 'row',
          cursor: this.props.onClick ? 'pointer' : 'default'
        }}
        onClick={this.onClick}
      >
        <div className={styles.iconColumn}>
          {this.renderSeverityIcon()}
        </div>
        <Row
          type="flex"
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            wordBreak: 'break-word'
          }}
        >
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
              style={{
                cursor: 'pointer',
                padding: '5px 0 0 10px'
              }}
            />
          </Row>
          <Row style={{
            maxHeight: 250,
            overflow: 'hidden'
          }}>
            <PreviewNotification
              text={this.props.notification.body}
              sanitize={this.props.notification.type === NOTIFICATION_TYPE.message}
            />
          </Row>
          <Row type="flex" justify="end">
            <span
              className={classNames(styles.date, 'cp-text-not-important')}
            >
              {displayDate(this.props.notification.createdDate)}
            </span>
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
