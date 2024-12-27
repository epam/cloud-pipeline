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
import {InputNumber} from 'antd';
import DaySelector from './day-selector';
import styles from './styles.css';

export default class MonthlyForm extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    schedule: PropTypes.shape({}),
    onChange: PropTypes.func
  };

  onChangeEvery = (value) => {
    const {schedule = {}, onChange} = this.props;
    onChange && onChange({
      ...schedule,
      every: value
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
          Every
          <InputNumber
            disabled={disabled}
            min={1}
            max={12}
            onChange={this.onChangeEvery}
            value={schedule.every}
            size="small"
            style={{margin: '0 3px', width: 60}}
          />
          month(s)
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
