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
import {Button, Icon, InputNumber, Modal, Row, Select, TimePicker} from 'antd';
import {observer} from 'mobx-react';
import moment from 'moment';

import styles from './launch-schedule.css';

const actions = {
  pause: 'PAUSE',
  resume: 'RESUME'
};

const ruleModes = {
  daily: 'daily',
  weekly: 'weekly'
};

//  every X days + time
//  every [weekday] + time

@observer
export default class RunScheduleDialog extends React.Component {
  static propTypes = {
    rules: PropTypes.array,
    visible: PropTypes.bool,
    disabled: PropTypes.bool,
    onSubmit: PropTypes.func
  };

  state = {
    rules: []
  };

  componentDidUpdate (prevProps, prevState) {
    if (this.props !== prevProps) {
      this.prepareState();
    }
  }

  prepareState = (props = this.props) => {
    const convertRule = ({action, cronExpression, id, timeZone}) => {
      const schedule = CronConverter.convertToRuleScheduleObject(cronExpression);

      return {
        id,
        timeZone,
        action,
        schedule
      };
    };

    const rules = (props.rules || []).map(convertRule);

    this.setState({rules});
  };

  getValidationRow = (field) => {
    if (this.state.validation[field]) {
      return (
        <Row
          key={`${field} validation`}
          type="flex"
          align="middle"
          style={{paddingLeft: 115, fontSize: 'smaller', color: 'red'}}>
          {this.state.validation[field]}
        </Row>
      );
    }
    return null;
  };

  validate = () => {
    return true;
  };

  onOkClicked = () => {
    const {disabled, onSubmit, onClose} = this.props;
    if (disabled) {
      onClose && onClose();
      return;
    }
    if (this.validate()) {
      const {rules} = this.state;
      const convertRule = ({action, schedule, id, timeZone}) => {
        const cronExpression = CronConverter.convertToCronString(schedule);

        return {
          id,
          action,
          timeZone,
          cronExpression
        };
      };

      const result = (rules || []).map(convertRule);

      onSubmit && onSubmit(result);
    }
  };

  onAddRow = () => {
    const {rules} = this.state;
    // todo timeZone?
    rules.push({
      schedule: {
        mode: ruleModes.daily,
        every: 1,
        time: {
          hours: 0,
          minutes: 0
        }
      },
      action: actions.pause
    });
    this.setState({rules});
  };

  onRuleRemove = (index) => {
    const {rules} = this.state;

    if (rules[index]) {
      rules.splice(index, 1);
      this.setState({rules});
    }
  };

  renderActionSelector = ({action}, i) => {
    const onActionChange = (value) => {
      const {rules} = this.state;

      rules[i].action = value;
      this.setState({rules});
    };
    return (
      <div>
        <Select
          onSelect={onActionChange}
          value={action}
          size="small"
          style={{width: 80}}
        >
          <Select.Option key={actions.pause}>Pause</Select.Option>
          <Select.Option key={actions.resume}>Resume</Select.Option>
        </Select>
      </div>
    );
  };

  renderScheduleModeSelector = ({schedule}, i) => {
    const onModeChange = (value) => {
      const {rules} = this.state;

      rules[i].schedule.mode = value;
      if (value === ruleModes.daily) {
        rules[i].schedule.every = 1;
        delete rules[i].schedule.dayOfWeek;
      } else {
        rules[i].schedule.dayOfWeek = [];
        delete rules[i].schedule.every;
      }
      this.setState({rules});
    };
    return (
      <div>
        <Select
          onSelect={onModeChange}
          value={schedule.mode}
          size="small"
          style={{width: 80, marginLeft: 15}}
        >
          <Select.Option key={ruleModes.daily}>Daily</Select.Option>
          <Select.Option key={ruleModes.weekly}>Weekly</Select.Option>
        </Select>
      </div>
    );
  };

  renderScheduleEverySelector = ({schedule}, i) => {
    if (schedule.mode !== ruleModes.daily) {
      return null;
    }
    const onEveryChange = (value) => {
      const {rules} = this.state;

      rules[i].schedule.every = value;
      this.setState({rules});
    };
    return (
      <div style={{flex: 1, marginLeft: 15}}>
        Every
        <InputNumber
          min={1}
          max={31}
          onChange={onEveryChange}
          value={schedule.every}
          size="small"
          style={{margin: '0 10px 0 10px', width: 50}}
        />
        day(s)
      </div>
    );
  };

  renderDayOfWeekSelector = ({schedule}, i) => {
    if (schedule.mode !== ruleModes.weekly) {
      return null;
    }
    const onDayOfWeekSelect = (value) => {
      const {rules} = this.state;

      rules[i].schedule.dayOfWeek.push(value);
      rules[i].schedule.dayOfWeek.sort();
      this.setState({rules});
    };
    const onDayOfWeekDeselect = (value) => {
      const {rules} = this.state;

      const index = rules[i].schedule.dayOfWeek.indexOf(value);
      if (index >= 0) {
        rules[i].schedule.dayOfWeek.splice(index, 1);
        this.setState({rules});
      }
    };

    return (
      <div style={{flex: 1, marginLeft: 15}}>
        <Select
          mode="multiple"
          onDeselect={onDayOfWeekDeselect}
          onSelect={onDayOfWeekSelect}
          value={schedule.dayOfWeek || '1'}
          size="small"
          style={{width: 170}}
        >
          <Select.Option key={'1'}>Monday</Select.Option>
          <Select.Option key={'2'}>Tuesday</Select.Option>
          <Select.Option key={'3'}>Wednesday</Select.Option>
          <Select.Option key={'4'}>Thursday</Select.Option>
          <Select.Option key={'5'}>Friday</Select.Option>
          <Select.Option key={'6'}>Saturday</Select.Option>
          <Select.Option key={'0'}>Sunday</Select.Option>
        </Select>
      </div>
    );
  };

  renderTimePicker = ({schedule}, i) => {
    const onTimeChange = (moment, timeString) => {
      const {rules} = this.state;
      const [hours, minutes] = timeString.split(':');

      rules[i].schedule.time = {hours, minutes};
      this.setState({rules});
    };
    const format = 'HH:mm';
    return (
      <div style={{marginLeft: 15}}>
        at
        <TimePicker
          format={format}
          onChange={onTimeChange}
          value={moment(`${schedule.time.hours}:${schedule.time.minutes}`, format)}
          size="small"
          style={{marginLeft: 10, width: 70}}
        />
      </div>
    );
  };

  renderRule = (rule, i) => {
    return (
      <Row
        key={i}
        type="flex"
        justify="space-between"
        className={styles.ruleRow}
        style={{padding: 5, width: '100%'}}
      >
        {this.renderActionSelector(rule, i)}
        {this.renderScheduleModeSelector(rule, i)}
        {this.renderScheduleEverySelector(rule, i)}
        {this.renderDayOfWeekSelector(rule, i)}
        {this.renderTimePicker(rule, i)}
        <Button
          onClick={() => { this.onRuleRemove(i); }}
          shape="circle"
          icon="delete"
          size="small"
          style={{marginLeft: 15}}
          type="danger"
        />
      </Row>
    );
  };

  render () {
    const {onClose, visible} = this.props;
    const {rules} = this.state;

    return (
      <Modal
        title="Run schedule"
        onCancel={onClose}
        onOk={this.onOkClicked}
        visible={visible}
        width={570}>
        <Row type="flex" style={{maxHeight: 400, overflowY: 'auto', overflowX: 'hidden'}}>
          {rules.map(this.renderRule)}
        </Row>
        <Row type="flex" style={{padding: 5}}>
          <Button size="small" onClick={this.onAddRow}><Icon type="plus" /> Add rule</Button>
        </Row>
      </Modal>
    );
  }
}

class CronConverter {
  static _getCronParts (expression) {
    if (!expression || expression.length === 0) {
      return null;
    }
    const parts = expression.split(' ').filter(Boolean);
    if (parts.length > 5) {
      const [, minutes, hours, dayOfMonth, , dayOfWeek] = parts;
      return {
        minutes,
        hours,
        dayOfMonth,
        dayOfWeek
      };
    } else if (parts.length === 5) {
      const [minutes, hours, dayOfMonth, , dayOfWeek] = parts;

      return {
        minutes,
        hours,
        dayOfMonth,
        dayOfWeek
      };
    }
    return null;
  }

  static convertToRuleScheduleObject (cronExpression) {
    const cronParts = CronConverter._getCronParts(cronExpression);

    let time = {};
    if (!isNaN(+cronParts.minutes)) {
      time.minutes = +cronParts.minutes;
    }
    if (!isNaN(+cronParts.hours)) {
      time.hours = +cronParts.hours;
    }
    let mode;
    let every;
    let dayOfWeek;
    if ((cronParts.dayOfWeek === '*' || cronParts.dayOfWeek === '?') &&
      cronParts.dayOfMonth.includes('/')) {
      mode = ruleModes.daily;
      every = cronParts.dayOfMonth.split('/')[1];
    } else {
      mode = ruleModes.weekly;
      dayOfWeek = cronParts.dayOfWeek.replace('7', '0').includes(',')
        ? cronParts.dayOfWeek.split(',')
        : [cronParts.dayOfWeek];
    }
    // {
    //   mode: ruleModes.daily | ruleModes.weekly,
    //   dayOfWeek: [], | every: 1,
    //   time: {
    //     hours: 0,
    //     minutes: 0
    //   }
    // }
    return {
      mode,
      every,
      dayOfWeek,
      time
    };
  }

  /**
   * Returns cron expression according to given params
   * @param scheduleObject {Object}
   * @param scheduleObject.mode {ruleModes.weekly|ruleModes.daily} - rule recurrence mode (daily or weekly)
   * @param scheduleObject.dayOfWeek {null|Array} - An array of day(s) of week for weekly recurrence mode
   * @param scheduleObject.every {null|Number|String} - day of month for daily recurrence mode
   * @param scheduleObject.time {Object} - time object
   * @param scheduleObject.time.hours {Number|String} - hours
   * @param scheduleObject.time.minutes {Number|String} - minutes
   * @param cronLength {Number} - Resulting cron expression string format (5, 6 or 7 parts)
   * @return {String|null} - Cron expression string
   * */
  static convertToCronString ({
    mode,
    dayOfWeek,
    every,
    time: {
      hours,
      minutes
    }
  },
  cronLength = 5
  ) {
    let cron5;
    if (mode === ruleModes.daily) {
      cron5 = `${minutes} ${hours} */${every} * ?`;
    }
    if (mode === ruleModes.weekly) {
      cron5 = `${minutes} ${hours} ? * ${dayOfWeek.sort().join(',')}`;
    }
    if (cronLength === 6) {
      return `0 ${cron5}`;
    }
    if (cronLength === 7) {
      return `0 ${cron5} *`;
    }

    return cron5 || null;
  }
}
