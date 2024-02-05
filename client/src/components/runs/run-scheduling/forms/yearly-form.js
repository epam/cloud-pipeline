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
import PropTypes from 'prop-types';
import {Select} from 'antd';
import {MONTHS} from './days.js';
import DaySelector from './day-selector';
import styles from './styles.css';

export default class YearlyForm extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    schedule: PropTypes.shape({}),
    onChange: PropTypes.func
  };

  onChangeMonth = (month) => {
    const {schedule = {}, onChange} = this.props;
    onChange && onChange({
      ...schedule,
      month
    });
  };

  onChangeDaySelector = (schedule) => {
    const {onChange} = this.props;
    onChange && onChange(schedule);
  };

  render () {
    const {disabled, schedule} = this.props;
    return (
      <div className={styles.formContainer}>
        <div className={styles.everyContainer}>
          <span style={{marginRight: 5}}>Every</span>
          <Select
            value={schedule.month}
            style={{width: 110, marginRight: 5}}
            onChange={this.onChangeMonth}
            size="small"
          >
            {MONTHS.map(({title, key}) => (
              <Select.Option key={key} value={key}>
                {title}
              </Select.Option>
            ))}
          </Select>
        </div>
        <span style={{marginRight: 3}}>On:</span>
        <DaySelector
          disabled={disabled}
          schedule={schedule}
          onChange={this.onChangeDaySelector}
        />
      </div>
    );
  }
}
