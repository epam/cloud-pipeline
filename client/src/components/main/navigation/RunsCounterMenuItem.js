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
import classNames from 'classnames';
import {Icon, Button, Tooltip} from 'antd';
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
          className={
            classNames(
              this.props.className,
              'cp-runs-menu-item',
              {
                active: this.props.counter && this.props.counter.runsCount > 0
              }
            )
          }
          onClick={this.props.onClick}
        >
          <Icon
            type={this.props.icon}
          />
          {
            this.props.counter &&
            this.props.counter.runsCount > 0 &&
            <span>
              {this.props.counter.runsCount}
            </span>
          }
        </Button>
      </Tooltip>
    );
  }
}
