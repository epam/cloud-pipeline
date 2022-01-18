/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import moment from 'moment-timezone';
import PropTypes from 'prop-types';
import {DatePicker, Button, Icon} from 'antd';
import styles from './period-picker.css';

const PERIOD_TYPES = {
  day: 'Day',
  month: 'Month'
};

const shiftMonth = (currentDate, amount) => {
  if (amount > 0) {
    return moment(currentDate).add(amount, 'months').format('YYYY-MM');
  }
  return moment(currentDate).subtract(Math.abs(amount), 'months').format('YYYY-MM');
};

const shiftDay = (currentDate, amount) => {
  if (amount > 0) {
    return moment(currentDate).add(amount, 'days').format('YYYY-MM-DD');
  }
  return moment(currentDate).subtract(Math.abs(amount), 'days').format('YYYY-MM-DD');
};

function PeriodPicker ({
  type,
  onPeriodChange,
  filters,
  className
}) {
  const onChange = (date) => {
    const format = filters.periodType === PERIOD_TYPES.day
      ? 'YYYY-MM-DD'
      : 'YYYY-MM';
    onPeriodChange && onPeriodChange(moment(date).format(format));
  };
  const renderPicker = () => {
    if (type === PERIOD_TYPES.month) {
      return (
        <DatePicker.MonthPicker
          className={styles.periodPicker}
          onChange={onChange}
          value={moment(filters.period)}
          allowClear={false}
        />
      );
    }
    return (
      <DatePicker
        className={styles.periodPicker}
        onChange={onChange}
        value={moment(filters.period)}
        allowClear={false}
      />
    );
  };
  const canNavigateRight = () => {
    const today = moment();
    if (filters.periodType === 'Day') {
      const diff = today.diff(filters.period, 'hours');
      return diff >= 24;
    }
    const diff = today.diff(filters.period, 'months');
    return diff >= 1;
  };
  const navigateLeft = () => {
    if (filters.periodType === 'Day') {
      return onPeriodChange && onPeriodChange(shiftDay(filters.period, -1));
    }
    return onPeriodChange && onPeriodChange(shiftMonth(filters.period, -1));
  };
  const navigateRight = () => {
    if (filters.periodType === 'Day') {
      return onPeriodChange && onPeriodChange(shiftDay(filters.period, 1));
    }
    return onPeriodChange && onPeriodChange(shiftMonth(filters.period, 1));
  };
  return (
    <div className={styles.container}>
      <Button
        className={styles.navigateLeftBtn}
        style={{paddingLeft: 8}}
        onClick={navigateLeft}
      >
        <Icon type="left" />
      </Button>
      {renderPicker()}
      <Button
        className={styles.navigateRightBtn}
        style={{paddingRight: 8}}
        onClick={navigateRight}
        disabled={!canNavigateRight()}
      >
        <Icon type="right" />
      </Button>
    </div>
  );
}

PeriodPicker.PropTypes = {
  type: PropTypes.string,
  filters: PropTypes.shape({
    periodType: PropTypes.string,
    period: PropTypes.string
  }),
  onPeriodChange: PropTypes.func
};

export default PeriodPicker;
export {PERIOD_TYPES};
