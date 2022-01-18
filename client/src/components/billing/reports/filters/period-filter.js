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
import {observer} from 'mobx-react';
import BillingNavigation from '../../navigation';
import DateFilter from '../../../special/reports/filters/period-filter';

function PeriodFilter ({
  filters = {},
  periods
}) {
  const {
    period: filter,
    range,
    periodNavigation: onChange = () => {}
  } = filters;
  return (
    <DateFilter
      filter={filter}
      range={range}
      onChange={onChange}
      periods={periods}
    />);
}

export default BillingNavigation.attach(observer(PeriodFilter));
