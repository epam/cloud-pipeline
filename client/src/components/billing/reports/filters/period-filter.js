/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button} from 'antd';
import Divider from './divider';
import RangeFilter from './range-filter';
import {Period, getPeriod, Range} from '../periods';
import styles from './period-filter.css';

export default function ({
  onChange,
  filter,
  range
}) {
  const getButtonType = (key) => key === filter ? 'primary' : 'default';
  const onClick = (key) => () => {
    if (key !== filter) {
      if (key === Period.custom) {
        const {start, end} = getPeriod(filter, range);
        onChange(key, Range.build({start, end}, Period.custom));
      } else {
        onChange(key);
      }
    }
  };
  const getPeriodName = (period) => {
    switch (period) {
      case Period.custom: return 'Custom';
      case Period.year: return 'Year';
      case Period.quarter: return 'Quarter';
      case Period.month: return 'Month';
    }
    return period;
  };
  return (
    <div style={{display: 'flex'}}>
      <RangeFilter
        period={filter}
        range={range}
        onChange={onChange}
      />
      <Divider />
      <Button.Group className={styles.periodBtnGroup}>
        {
          [Period.custom, Period.year, Period.quarter, Period.month].map(period => (
            <Button
              id={`set-period-${period}`}
              className={styles.button}
              key={period}
              type={getButtonType(period)}
              onClick={onClick(period)}
            >
              {getPeriodName(period)}
            </Button>
          ))
        }
      </Button.Group>
    </div>
  );
}
