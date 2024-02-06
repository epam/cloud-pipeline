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
import styles from './styles.css';

export default class DailyForm extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    schedule: PropTypes.shape({}),
    onChange: PropTypes.func
  };

  onChange = (value) => {
    const {onChange, schedule} = this.props;
    onChange && onChange({
      ...schedule,
      every: value
    });
  };

  render () {
    const {disabled, schedule} = this.props;
    return (
      <div className={styles.formContainer}>
        Every
        <InputNumber
          disabled={disabled}
          min={1}
          max={366}
          onChange={this.onChange}
          value={schedule.every}
          size="small"
          style={{margin: '0 3px', width: 60}}
        />
        day(s)
      </div>
    );
  }
}
