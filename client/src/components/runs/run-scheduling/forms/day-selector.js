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
import {Radio, InputNumber, Select} from 'antd';
import PropTypes from 'prop-types';
import {COMPUTED_DAYS, ORDINALS, DAYS} from './days';
import styles from './styles.css';

export const DAY_SELECTOR_MODES = {
  numeric: 'numeric',
  computed: 'computed'
};

export default class DaySelector extends React.Component {
  static propTypes = {
    schedule: PropTypes.shape({
      daySelectorMode: PropTypes.string,
      every: PropTypes.number
    }),
    disabled: PropTypes.bool,
    maximumDays: PropTypes.number
  };

  onChangeDaySelectorMode = (event) => {
    const {schedule, onChange} = this.props;
    onChange && onChange({
      ...schedule,
      daySelectorMode: event.target.value
    });
  };

  onChangeOrdinal = (value) => {
    const {schedule, onChange} = this.props;
    onChange && onChange({
      ...schedule,
      ordinal: value,
      day: schedule.day === COMPUTED_DAYS.day.key ? undefined : schedule.day
    });
  };

  onChangeDay = (value) => {
    const {schedule, onChange} = this.props;
    onChange && onChange({
      ...schedule,
      day: value
    });
  };

  onChangeDayNumber = (value) => {
    const {schedule, onChange} = this.props;
    onChange && onChange({
      ...schedule,
      dayNumber: value
    });
  };

  render () {
    const {disabled, schedule, maximumDays} = this.props;
    return (
      <div className={styles.daySelectorContainer}>
        <div className={styles.section}>
          <Radio
            checked={schedule.daySelectorMode === DAY_SELECTOR_MODES.numeric}
            value={DAY_SELECTOR_MODES.numeric}
            onChange={this.onChangeDaySelectorMode}
          />
          <InputNumber
            disabled={disabled || schedule.daySelectorMode !== DAY_SELECTOR_MODES.numeric}
            min={1}
            max={maximumDays || 31}
            onChange={this.onChangeDayNumber}
            value={schedule.dayNumber}
            size="small"
            style={{width: 75}}
          />
          <span style={{marginLeft: 3}}>Day</span>
        </div>
        <div className={styles.section}>
          <Radio
            checked={schedule.daySelectorMode === DAY_SELECTOR_MODES.computed}
            value={DAY_SELECTOR_MODES.computed}
            onChange={this.onChangeDaySelectorMode}
          />
          <Select
            value={schedule.ordinal}
            style={{width: 75, marginRight: 5}}
            onChange={this.onChangeOrdinal}
            disabled={disabled || schedule.daySelectorMode !== DAY_SELECTOR_MODES.computed}
            size="small"
          >
            {ORDINALS.map(ordinal => (
              <Select.Option key={ordinal.cronCode} value={ordinal.cronCode}>
                {ordinal.title}
              </Select.Option>
            ))}
          </Select>
          <Select
            value={schedule.day}
            style={{width: 110}}
            onChange={this.onChangeDay}
            disabled={disabled || schedule.daySelectorMode !== DAY_SELECTOR_MODES.computed}
            size="small"
          >
            {[
              ...DAYS,
              COMPUTED_DAYS.weekday,
              schedule.ordinal === 'L' ? COMPUTED_DAYS.day : undefined
            ]
              .filter(Boolean)
              .map(({key, title}) => (
                <Select.Option key={key} value={key}>
                  {title}
                </Select.Option>
              ))}
          </Select>
        </div>
      </div>
    );
  }
}
