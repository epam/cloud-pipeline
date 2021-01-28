/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Icon,
  Select,
  TimePicker
} from 'antd';
import classNames from 'classnames';
import moment from 'moment-timezone';
import formStyles from './edit-hot-node-pool.css';
import styles from './schedule-control.css';

const Dates = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

function compareSchedulesArray (schedulesA, schedulesB) {
  if (!schedulesA && !schedulesB) {
    return true;
  }
  if (!schedulesA || !schedulesB) {
    return false;
  }
  if (schedulesA.length !== schedulesB.length) {
    return false;
  }
  for (let i = 0; i < schedulesA.length; i++) {
    if (!compareSchedules(schedulesA[i], schedulesB[i])) {
      return false;
    }
  }
  return true;
}

function compareSchedules (scheduleA, scheduleB) {
  if (!scheduleA && !scheduleB) {
    return true;
  }
  if (!scheduleA || !scheduleB) {
    return false;
  }
  const {
    from: startA,
    fromTime: startTimeA,
    to: endA,
    toTime: endTimeA
  } = scheduleA;
  const {
    from: startB,
    fromTime: startTimeB,
    to: endB,
    toTime: endTimeB
  } = scheduleB;
  return startA === startB &&
    startTimeA === startTimeB &&
    endA === endB &&
    endTimeA === endTimeB;
}

function scheduleIsValid (schedule) {
  if (!schedule) {
    return false;
  }
  const {
    from,
    fromTime,
    to,
    toTime
  } = schedule;
  return from && fromTime && to && toTime;
}

function parse (date) {
  if (!date) {
    return undefined;
  }
  const localTime = moment.utc(date, 'HH:mm:ss').toDate();
  return moment(localTime);
}

function getDayIndex (day) {
  return Dates.map(d => d.toUpperCase()).indexOf((day || '').toUpperCase());
}

function getDay (index) {
  if (index >= 0 && index < Dates.length) {
    return (Dates[index] || '').toUpperCase();
  }
  return undefined;
}

function parseDay (day, date) {
  if (date && day) {
    const curr = moment.utc(date, 'HH:mm:ss').get('d');
    const act = moment.utc(date, 'HH:mm:ss').add(moment().utcOffset(), 'm').get('d');
    return getDay((getDayIndex(day) + (act - curr) + Dates.length) % Dates.length);
  }
  return day;
}

function formatTime (date) {
  if (!date) {
    return undefined;
  }
  const localTime = moment.utc(date, 'HH:mm:ss').toDate();
  localTime.setSeconds(0);
  return moment(localTime);
}

function formatDay (day, date) {
  if (date && day) {
    const curr = moment.utc(date).get('d');
    const act = moment.utc(date).add(moment().utcOffset(), 'm').get('d');
    return getDay((getDayIndex(day) + (curr - act) + Dates.length) % Dates.length);
  }
  return day;
}

class ScheduleControl extends React.Component {
  state = {
    from: undefined,
    fromTime: undefined,
    to: undefined,
    toTime: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.schedule !== this.props.schedule) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {schedule} = this.props;
    if (schedule) {
      const {
        from,
        fromTime,
        to,
        toTime
      } = schedule;
      this.setState({
        from: parseDay(from, fromTime),
        fromTime: parse(fromTime),
        to: parseDay(to, toTime),
        toTime: parse(toTime)
      });
    } else {
      this.setState({
        from: undefined,
        fromTime: undefined,
        to: undefined,
        toTime: undefined
      });
    }
  };

  onChangeFrom = (value) => {
    this.setState({
      from: value
    }, this.handleChange);
  };

  onChangeFromTime = (time) => {
    this.setState({
      fromTime: formatTime(time)
    }, this.handleChange);
  };

  onChangeTo = (value) => {
    this.setState({
      to: value
    }, this.handleChange);
  };

  onChangeToTime = (time) => {
    this.setState({
      toTime: formatTime(time)
    }, this.handleChange);
  };

  handleChange = () => {
    const {from, fromTime, to, toTime} = this.state;
    const {onChange} = this.props;
    if (onChange) {
      onChange({
        from: formatDay(from, fromTime),
        fromTime: fromTime ? moment.utc(fromTime).format('HH:mm:ss') : undefined,
        to: formatDay(to, toTime),
        toTime: toTime ? moment.utc(toTime).format('HH:mm:ss') : undefined
      });
    }
  };

  render () {
    const {className, onRemove, invalid} = this.props;
    const {
      from,
      fromTime,
      to,
      toTime
    } = this.state;
    return (
      <div
        className={className}
        style={{justifyContent: 'space-between'}}
      >
        <div>
          <span className={formStyles.label}>Starts on:</span>
          <Select
            showSearch
            className={classNames({[styles.invalid]: invalid})}
            style={{width: 200}}
            value={from}
            onChange={this.onChangeFrom}
            filterOption={(input, option) =>
              option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
          >
            {
              Dates.map((date) => (
                <Select.Option
                  key={date}
                  value={date.toUpperCase()}
                >
                  {date}
                </Select.Option>
              ))
            }
          </Select>
          <TimePicker
            className={classNames({[styles.invalid]: invalid})}
            style={{marginLeft: 5}}
            value={fromTime}
            format="HH:mm"
            onChange={this.onChangeFromTime}
          />
          <span
            className={formStyles.label}
            style={{textAlign: 'right'}}
          >
            Ends on:
          </span>
          <Select
            className={classNames({[styles.invalid]: invalid})}
            showSearch
            style={{width: 200}}
            value={to}
            onChange={this.onChangeTo}
            filterOption={(input, option) =>
              option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
          >
            {
              Dates.map((date) => (
                <Select.Option
                  key={date}
                  value={date.toUpperCase()}
                >
                  {date}
                </Select.Option>
              ))
            }
          </Select>
          <TimePicker
            className={classNames({[styles.invalid]: invalid})}
            style={{marginLeft: 5}}
            value={toTime}
            format="HH:mm"
            onChange={this.onChangeToTime}
          />
        </div>
        <Button
          size="small"
          type="danger"
          onClick={onRemove}
        >
          <Icon type="delete" />
        </Button>
      </div>
    );
  }
}

ScheduleControl.propTypes = {
  schedule: PropTypes.object,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  className: PropTypes.string,
  invalid: PropTypes.bool
};

export {compareSchedulesArray, scheduleIsValid, parseDay};
export default ScheduleControl;
