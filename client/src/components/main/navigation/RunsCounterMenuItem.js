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
import {inject, observer} from 'mobx-react';
import {Icon, Row, Button, Tooltip} from 'antd';
import styles from './Navigation.css';
import PropTypes from 'prop-types';

@inject('counter')
@observer
export default class RunsCounterMenuItem extends React.Component {
  static propTypes = {
    onClick: PropTypes.func,
    icon: PropTypes.string,
    className: PropTypes.string,
    highlightedClassName: PropTypes.string,
    tooltip: PropTypes.string
  };
  render () {
    return (
      <Tooltip overlay={this.props.tooltip} placement="right" mouseEnterDelay={0.5}>
        <Button
          id="navigation-button-runs"
          style={{display: 'block', margin: '0 2px', textDecoration: 'none'}}
          className={
            this.props.counter &&
            this.props.counter.value > 0
              ? this.props.highlightedClassName : this.props.className
          }
          onClick={this.props.onClick}
        >
          <Row
            type="flex"
            justify="center"
            align="middle"
            style={{height: '100%'}}>
            <Icon
              type={this.props.icon}
              className={
                this.props.counter &&
                this.props.counter.value > 0
                ? styles.highlightedIcon : styles.icon
              } />
            {
              this.props.counter &&
              this.props.counter.value > 0 &&
              <span className={styles.counterBadge}>{this.props.counter.value}</span>
            }
          </Row>
        </Button>
      </Tooltip>
    );
  }
}
