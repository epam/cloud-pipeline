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
import PropTypes from 'prop-types';
import {QuarterPicker, Quarters, RangePicker, YearPicker, MonthPicker} from './components';
import {Period, Range, getPeriod} from '../periods';

function rangeFilter ({period, range, onChange}) {
  const onChangeDate = (date) => {
    if (onChange) {
      const {start, end} = Range.buildRangeByDate(date, period);
      const queryString = Range.build({start, end}, period);
      onChange(
        period,
        queryString
      );
    }
  };

  const onCustomRangeChanged = (start, end) => {
    if (onChange) {
      const queryString = Range.build({start, end}, period);
      onChange(
        period,
        queryString
      );
    }
  };

  const {start} = Range.parse(range, period);
  const {start: defaultStart} = getPeriod(period, range);
  if (period === Period.quarter) {
    return (
      <QuarterPicker
        title={
          `${
            Quarters && Quarters[defaultStart.format('Q')]
              ? Quarters[defaultStart.format('Q')]
              : defaultStart.format('Q')
          } quarter, ${defaultStart.format('YYYY')}`
        }
        value={start}
        onChange={onChangeDate}
      />
    );
  }
  if (period === Period.year) {
    return (
      <YearPicker
        title={`${defaultStart.format('YYYY')} year`}
        value={start}
        onChange={onChangeDate}
      />
    );
  }
  if (period === Period.month) {
    return (
      <MonthPicker
        title={defaultStart.format('MMM YYYY')}
        value={start}
        onChange={onChangeDate}
      />
    );
  }
  if (period === Period.custom) {
    return (
      <RangePicker
        range={range}
        onChange={onCustomRangeChanged}
      />
    );
  }
  return null;
}

rangeFilter.propTypes = {
  disabled: PropTypes.bool,
  period: PropTypes.string,
  range: PropTypes.string,
  onChange: PropTypes.func
};

export default rangeFilter;
