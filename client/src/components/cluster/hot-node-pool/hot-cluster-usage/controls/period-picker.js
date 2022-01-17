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
import {DatePicker} from 'antd';

const PERIOD_TYPES = {
  day: 'Day',
  month: 'Month'
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
  if (type === PERIOD_TYPES.month) {
    return (
      <DatePicker.MonthPicker
        onChange={onChange}
        value={moment(filters.period)}
        className={className}
        allowClear={false}
      />
    );
  }
  return (
    <DatePicker
      onChange={onChange}
      value={moment(filters.period)}
      className={className}
      allowClear={false}
    />
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
