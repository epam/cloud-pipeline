/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {Popover} from 'antd';
import moment from 'moment-timezone';

const SECOND = 1;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

function getDurationPresentationByUnit (duration, unitValue, unit) {
  if (duration >= unitValue) {
    const count = Math.floor(duration / unitValue);
    return `${count} ${unit}${count === 1 ? '' : 's'}`;
  }
  return '';
}

const durationStops = [
  {value: DAY, label: 'day', details: HOUR, detailsLabel: 'hour'},
  {value: HOUR, label: 'hour', details: MINUTE, detailsLabel: 'minute'},
  {value: MINUTE, label: 'minute', details: SECOND, detailsLabel: 'second'}
];

function getDurationPresentation (duration) {
  const parts = [];
  const stopIndex = durationStops.findIndex((aStop) => aStop.value <= duration);
  if (stopIndex === -1) {
    return `Less than a minute`;
  }
  const stop = durationStops[stopIndex];
  if (duration >= 2 * stop.value) {
    return getDurationPresentationByUnit(duration, stop.value, stop.label);
  }
  if (duration >= stop.value) {
    parts.push(`1 ${stop.label}`);
    const rest = duration - stop.value;
    parts.push(getDurationPresentationByUnit(rest, stop.details, stop.detailsLabel));
    return parts.filter((part) => !!part && part.length > 0).join(', ');
  }
  return getDurationPresentation(duration, SECOND, 'second');
}

class RunTagPopover extends React.PureComponent {
  state = {
    visible: false,
    duration: 0
  };

  onVisibilityChanged = (visible) => {
    const {date} = this.props;
    this.setState({
      visible,
      duration: date && date.isValid() ? moment.utc().diff(date, 's') : 0
    });
  };

  render () {
    const {
      children,
      date,
      value
    } = this.props;
    if (!date && (value === undefined || value === null)) {
      return children;
    }
    const {
      visible,
      duration
    } = this.state;
    return (
      <Popover
        visible={visible}
        onVisibleChange={this.onVisibilityChanged}
        content={(
          <div>
            {
              value !== undefined && value !== null && (
                <div>
                  {value}
                </div>
              )
            }
            {
              date && (
                <div>
                  {getDurationPresentation(duration)}
                </div>
              )
            }
          </div>
        )}
      >
        {children}
      </Popover>
    );
  }
}

RunTagPopover.propTypes = {
  children: PropTypes.node,
  date: PropTypes.object,
  value: PropTypes.node,
  tag: PropTypes.string
};

export default RunTagPopover;
