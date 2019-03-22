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

import React, {Component} from 'react';
import PropTypes from 'prop-types';
import styles from './StatusIcon.css';
import {Icon} from 'antd';

export default class StatusIcon extends Component {
  static propTypes = {
    status: PropTypes.string,
    run: PropTypes.shape({
      status: PropTypes.string,
      initialized: PropTypes.bool,
      instance: PropTypes.shape({
        nodeIP: PropTypes.string
      }),
      podIP: PropTypes.string
    }),
    info: PropTypes.shape({
      endDate: PropTypes.string,
      name: PropTypes.string,
      pipelineId: PropTypes.number,
      runId: PropTypes.number,
      runParams: PropTypes.string,
      version: PropTypes.string
    }),
    className: PropTypes.string,
    small: PropTypes.bool,
    colors: PropTypes.object,
    iconSet: PropTypes.object
  };

  render () {
    let icon, style;
    let blink = false;

    let status = this.props.status;
    if (!status && this.props.run) {
      status = this.props.run.status || '';
      if (status.toUpperCase() === 'RUNNING' &&
        this.props.run.instance &&
        (this.props.run.instance.nodeIP || this.props.run.instance.nodeName) &&
        (!this.props.run.podIP && !this.props.run.initialized)) {
        status = 'PULLING';
      } else if (status.toUpperCase() === 'RUNNING' &&
        (
          !this.props.run.instance ||
          !this.props.run.instance.nodeIP ||
          !this.props.run.instance.nodeName
        ) && !this.props.run.initialized) {
        status = 'SCHEDULED';
      }
    }

    switch ((status || '').toUpperCase()) {
      case 'SUCCESS':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'check-circle-o';
        style = 'iconGreen';
        break;
      case 'FAILURE':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'exclamation-circle';
        style = 'iconRed';
        break;
      case 'RUNNING':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'play-circle-o';
        style = 'iconBlue';
        break;
      case 'RESUMING':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'play-circle-o';
        style = 'iconBlue';
        blink = true;
        break;
      case 'PAUSING':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'pause-circle-o';
        style = 'iconBlue';
        blink = true;
        break;
      case 'PAUSED':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'pause-circle-o';
        style = 'iconBlue';
        break;
      case 'SCHEDULED':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'loading';
        style = 'iconBlue';
        break;
      case 'PULLING':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'download';
        style = 'iconBlue';
        break;
      case 'STOPPED':
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'clock-circle';
        style = 'iconYellow';
        break;
      default:
        icon = this.props.iconSet && this.props.iconSet[(status || '').toLowerCase()]
          ? this.props.iconSet[(status || '').toLowerCase()]
          : 'play-circle-o';
        style = 'iconYellow';
        break;
    }

    const additionalStyle = this.props.colors && this.props.colors[(status || '').toLowerCase()]
      ? this.props.colors[(status || '').toLowerCase()]
      : {};

    const iconStyle = {verticalAlign: 'middle', fontWeight: 'normal'};
    if (this.props.small) {
      iconStyle.fontSize = 'small';
    }
    const className = blink ? `${styles[style]} ${styles.blink}` : styles[style];
    return <Icon className={className} type={icon} style={Object.assign({}, iconStyle, additionalStyle)} />;
  }
}
